/*
 * Copyright 2021 Google LLC
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

package com.google.android.gnd;

import static com.google.android.gnd.FakeData.TERMS_OF_SERVICE;
import static com.google.android.gnd.FakeData.TEST_USER;
import static com.google.common.truth.Truth.assertThat;

import android.content.SharedPreferences;
import androidx.navigation.NavDirections;
import com.google.android.gnd.repository.TermsOfServiceRepository;
import com.google.android.gnd.repository.UserRepository;
import com.google.android.gnd.system.auth.FakeAuthenticationManager;
import com.google.android.gnd.system.auth.SignInState;
import com.google.android.gnd.system.auth.SignInState.State;
import com.google.android.gnd.ui.common.Navigator;
import com.google.android.gnd.ui.home.HomeScreenFragmentDirections;
import com.google.android.gnd.ui.signin.SignInFragmentDirections;
import dagger.hilt.android.testing.HiltAndroidTest;
import io.reactivex.observers.TestObserver;
import java.util.NoSuchElementException;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowToast;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public class MainViewModelTest extends BaseHiltTest {

  @Inject FakeAuthenticationManager fakeAuthenticationManager;
  @Inject MainViewModel viewModel;
  @Inject Navigator navigator;
  @Inject SharedPreferences sharedPreferences;
  @Inject TermsOfServiceRepository tosRepository;
  @Inject UserRepository userRepository;

  private TestObserver<NavDirections> navDirectionsTestObserver;

  @Before
  public void setUp() {
    // TODO: Add a test for syncFeatures
    super.setUp();

    // Subscribe to navigation requests
    navDirectionsTestObserver = navigator.getNavigateRequests().test();
  }

  private void setupUserPreferences() {
    sharedPreferences.edit().putString("foo", "bar").apply();
  }

  private void assertUserPreferencesCleared() {
    assertThat(sharedPreferences.getAll()).isEmpty();
  }

  private void assertUserSaved() {
    userRepository.getUser(TEST_USER.getId()).test().assertResult(TEST_USER);
  }

  private void assertUserNotSaved() {
    userRepository.getUser(TEST_USER.getId()).test().assertError(NoSuchElementException.class);
  }

  private void assertProgressDialogVisible(boolean visible) {
    TestObservers.observeUntilFirstChange(viewModel.getSignInProgressDialogVisibility());
    assertThat(viewModel.getSignInProgressDialogVisibility().getValue()).isEqualTo(visible);
  }

  private void assertNavigate(NavDirections... navDirections) {
    navDirectionsTestObserver.assertNoErrors().assertNotComplete().assertValues(navDirections);
  }

  @Test
  public void testSignInStateChanged_onSignedOut() {
    setupUserPreferences();

    fakeAuthenticationManager.signOut();

    assertProgressDialogVisible(false);
    assertNavigate(SignInFragmentDirections.showSignInScreen());
    assertUserPreferencesCleared();
    assertUserNotSaved();
    assertThat(tosRepository.isTermsOfServiceAccepted()).isFalse();
  }

  @Test
  public void testSignInStateChanged_onSigningIn() {
    fakeAuthenticationManager.setState(new SignInState(State.SIGNING_IN));

    assertProgressDialogVisible(true);
    assertNavigate();
    assertUserNotSaved();
    assertThat(tosRepository.isTermsOfServiceAccepted()).isFalse();
  }

  @Test
  public void testSignInStateChanged_onSignedIn_whenTosAccepted() {
    tosRepository.setTermsOfServiceAccepted(true);
    fakeAuthenticationManager.setUser(TEST_USER);
    fakeAuthenticationManager.signIn();

    assertProgressDialogVisible(false);
    assertNavigate(HomeScreenFragmentDirections.showHomeScreen());
    assertUserSaved();
    assertThat(tosRepository.isTermsOfServiceAccepted()).isTrue();
  }

  @Test
  public void testSignInStateChanged_onSignedIn_whenTosNotAccepted() {
    tosRepository.setTermsOfServiceAccepted(false);
    fakeAuthenticationManager.setUser(TEST_USER);
    fakeAuthenticationManager.signIn();

    assertProgressDialogVisible(false);
    assertNavigate(
        SignInFragmentDirections.showTermsOfService().setTermsOfServiceText(TERMS_OF_SERVICE));
    assertUserSaved();
    assertThat(tosRepository.isTermsOfServiceAccepted()).isFalse();
  }

  @Test
  public void testSignInStateChanged_onSignInError() {
    setupUserPreferences();

    fakeAuthenticationManager.setState(new SignInState(new Exception()));

    assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Sign in unsuccessful");
    assertProgressDialogVisible(false);
    assertNavigate(SignInFragmentDirections.showSignInScreen());
    assertUserPreferencesCleared();
    assertUserNotSaved();
    assertThat(tosRepository.isTermsOfServiceAccepted()).isFalse();
  }
}
