/*
 * Copyright 2023 Google LLC
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
package com.google.android.ground.ui.datacollection.tasks.polygon

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnAttach
import androidx.fragment.app.activityViewModels
import com.google.android.ground.databinding.BasemapLayoutBinding
import com.google.android.ground.databinding.PolygonDrawingTaskFragBinding
import com.google.android.ground.model.geometry.Point
import com.google.android.ground.ui.MarkerIconFactory
import com.google.android.ground.ui.common.AbstractMapContainerFragment
import com.google.android.ground.ui.common.BaseMapViewModel
import com.google.android.ground.ui.datacollection.DataCollectionViewModel
import com.google.android.ground.ui.datacollection.tasks.TaskFragment
import com.google.android.ground.ui.map.CameraPosition
import com.google.android.ground.ui.map.MapFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.properties.Delegates

@AndroidEntryPoint
class PolygonDrawingTaskFragment :
  AbstractMapContainerFragment(), TaskFragment<PolygonDrawingViewModel> {
  private val dataCollectionViewModel: DataCollectionViewModel by activityViewModels()
  override lateinit var viewModel: PolygonDrawingViewModel
  override var position by Delegates.notNull<Int>()

  @Inject lateinit var markerIconFactory: MarkerIconFactory

  private lateinit var mapViewModel: BaseMapViewModel
  private lateinit var binding: BasemapLayoutBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState != null) {
      position = savedInstanceState.getInt(TaskFragment.POSITION)
    }
    mapViewModel = getViewModel(BaseMapViewModel::class.java)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putInt(TaskFragment.POSITION, position)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = BasemapLayoutBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.fragment = this
    binding.lifecycleOwner = this

    view.doOnAttach {
      viewModel = dataCollectionViewModel.getTaskViewModel(position) as PolygonDrawingViewModel
      binding.viewModel = mapViewModel

      val container = binding.bottomContainer
      val taskControlsBinding =
        PolygonDrawingTaskFragBinding.inflate(layoutInflater, container, true)
      taskControlsBinding.viewModel = viewModel
      taskControlsBinding.lifecycleOwner = this
      viewModel.startDrawingFlow()
    }
  }

  override fun onMapReady(mapFragment: MapFragment) {
    viewModel.features.observe(this) { mapFragment.renderFeatures(it) }
  }

  override fun getMapViewModel(): BaseMapViewModel = mapViewModel

  override fun onMapCameraMoved(position: CameraPosition) {
    super.onMapCameraMoved(position)
    val mapCenter = position.target
    val mapCenterPoint = Point(mapCenter)
    viewModel.onCameraMoved(mapCenterPoint)
    viewModel.firstVertex
      .map { firstVertex: Point ->
        mapFragment.getDistanceInPixels(firstVertex.coordinate, mapCenter)
      }
      .ifPresent { dist: Double -> viewModel.updateLastVertex(mapCenterPoint, dist) }
  }
}
