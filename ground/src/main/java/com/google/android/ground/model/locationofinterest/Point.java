/*
 * Copyright 2018 Google LLC
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

package com.google.android.ground.model.locationofinterest;

import com.google.auto.value.AutoValue;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

/** The location of a single point on the map. */
@AutoValue
public abstract class Point {
  private final GeometryFactory geometryFactory = new GeometryFactory();

  public abstract double getLatitude();

  public abstract double getLongitude();

  public static Builder newBuilder() {
    return new AutoValue_Point.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setLatitude(double newLatitude);

    public abstract Builder setLongitude(double newLongitude);

    public abstract Point build();
  }

  static Point fromCoordinate(Coordinate coordinate) {
    if (coordinate == null) {
      return zero();
    }
    return Point.newBuilder().setLatitude(coordinate.x).setLongitude(coordinate.y).build();
  }

  static Point zero() {
    return Point.newBuilder().setLatitude(0).setLongitude(0).build();
  }

  public org.locationtech.jts.geom.Point toGeometry() {
    return geometryFactory.createPoint(new Coordinate(this.getLatitude(), this.getLongitude()));
  }
}
