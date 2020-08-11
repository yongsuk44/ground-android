/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gnd.repository;

import static com.google.android.gnd.util.ImmutableListCollector.toImmutableList;
import static com.google.android.gnd.util.ImmutableSetCollector.toImmutableSet;
import static java8.util.stream.StreamSupport.stream;

import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gnd.model.Project;
import com.google.android.gnd.model.basemap.OfflineArea;
import com.google.android.gnd.model.basemap.OfflineArea.State;
import com.google.android.gnd.model.basemap.OfflineBaseMapSource;
import com.google.android.gnd.model.basemap.tile.Tile;
import com.google.android.gnd.persistence.geojson.GeoJsonParser;
import com.google.android.gnd.persistence.local.LocalDataStore;
import com.google.android.gnd.persistence.sync.TileDownloadWorkManager;
import com.google.android.gnd.persistence.uuid.OfflineUuidGenerator;
import com.google.android.gnd.rx.Loadable;
import com.google.android.gnd.ui.util.FileUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
import timber.log.Timber;

public class OfflineAreaRepository {
  private final TileDownloadWorkManager tileDownloadWorkManager;
  private final LocalDataStore localDataStore;
  private final ProjectRepository projectRepository;
  private final GeoJsonParser geoJsonParser;
  private final FileUtil fileUtil;

  private final OfflineUuidGenerator uuidGenerator;

  @Inject
  public OfflineAreaRepository(
      TileDownloadWorkManager tileDownloadWorkManager,
      LocalDataStore localDataStore,
      ProjectRepository projectRepository,
      GeoJsonParser geoJsonParser,
      OfflineUuidGenerator uuidGenerator,
      FileUtil fileUtil) {
    this.tileDownloadWorkManager = tileDownloadWorkManager;
    this.localDataStore = localDataStore;
    this.geoJsonParser = geoJsonParser;
    this.uuidGenerator = uuidGenerator;
    this.projectRepository = projectRepository;
    this.fileUtil = fileUtil;
  }

  /**
   * Download the offline basemap source for the active project.
   *
   * <p>Only the first basemap source is used. Sources are always re-downloaded and overwritten on
   * subsequent calls.
   */
  private File downloadOfflineBaseMapSource(
      ImmutableList<OfflineBaseMapSource> offlineBaseMapSources)
      throws IOException, NoBaseMapSourceException {
    if (offlineBaseMapSources.isEmpty()) {
      throw new NoBaseMapSourceException("No basemap sources specified for this project.");
    }

    OfflineBaseMapSource source = offlineBaseMapSources.get(0);
    URL baseMapUrl = source.getUrl();
    Timber.d("Basemap url: %s, file: %s", baseMapUrl, baseMapUrl.getFile());
    File localFile = fileUtil.makeFile(baseMapUrl.getFile());

    FileUtils.copyURLToFile(baseMapUrl, localFile);
    return localFile;
  }

  /** Enqueue a single area and its tiles for download. */
  private Completable enqueueDownload(OfflineArea area, ImmutableList<Tile> tiles) {
    return localDataStore
        .insertOrUpdateOfflineArea(area.toBuilder().setState(State.IN_PROGRESS).build())
        .andThen(
            Completable.merge(
                stream(tiles.asList())
                    .map(localDataStore::insertOrUpdateTile)
                    .collect(toImmutableList())))
        .doOnError(__ -> Timber.e("failed to add/update a tile in the database"))
        .andThen(tileDownloadWorkManager.enqueueTileDownloadWorker());
  }

  /**
   * Determine the set of tiles that need to be downloaded for a given area, then enqueue tile
   * downloads.
   */
  private Completable enqueueTileDownloads(OfflineArea area) {
    return projectRepository
        .getActiveProjectOnceAndStream()
        .compose(Loadable::values)
        .map(Project::getOfflineBaseMapSources)
        .map(this::downloadOfflineBaseMapSource)
        .map(json -> geoJsonParser.intersectingTiles(area.getBounds(), json))
        .flatMapCompletable(tiles -> enqueueDownload(area, tiles))
        .doOnError(throwable -> Timber.e(throwable, "failed to download area"))
        .onErrorComplete();
  }

  public Completable addAreaAndEnqueue(LatLngBounds bounds) {
    OfflineArea offlineArea =
        OfflineArea.newBuilder()
            .setBounds(bounds)
            .setId(uuidGenerator.generateUuid())
            .setState(State.PENDING)
            .build();

    return enqueueTileDownloads(offlineArea);
  }

  public Flowable<ImmutableList<OfflineArea>> getOfflineAreasOnceAndStream() {
    return localDataStore.getOfflineAreasOnceAndStream();
  }

  public Single<OfflineArea> getOfflineArea(String offlineAreaId) {
    return localDataStore.getOfflineAreaById(offlineAreaId);
  }

  public Flowable<ImmutableSet<Tile>> getIntersectingDownloadedTilesOnceAndStream(
      OfflineArea offlineArea) {
    return projectRepository
        .getActiveProjectOnceAndStream()
        .compose(Loadable::values)
        .map(Project::getOfflineBaseMapSources)
        .map(this::downloadOfflineBaseMapSource)
        .map(json -> geoJsonParser.intersectingTiles(offlineArea.getBounds(), json))
        .flatMap(
            tiles ->
                getDownloadedTilesOnceAndStream()
                    .map(ts -> stream(ts).filter(tiles::contains).collect(toImmutableSet())))
        .onErrorReturn(throwable -> ImmutableSet.of());
  }

  public Flowable<ImmutableSet<Tile>> getDownloadedTilesOnceAndStream() {
    return localDataStore
        .getTilesOnceAndStream()
        .map(
            set ->
                stream(set)
                    .filter(tile -> tile.getState() == Tile.State.DOWNLOADED)
                    .collect(toImmutableSet()));
  }

  private class NoBaseMapSourceException extends Exception {
    NoBaseMapSourceException(String message) {
      super(message);
    }
  }
}
