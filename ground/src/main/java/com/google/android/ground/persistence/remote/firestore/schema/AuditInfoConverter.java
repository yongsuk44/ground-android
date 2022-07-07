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

package com.google.android.ground.persistence.remote.firestore.schema;

import static com.google.android.ground.persistence.remote.DataStoreException.checkNotNull;

import androidx.annotation.NonNull;
import com.google.android.ground.model.AuditInfo;
import com.google.android.ground.model.User;
import com.google.android.ground.model.mutation.Mutation;
import com.google.android.ground.persistence.remote.DataStoreException;
import com.google.firebase.Timestamp;
import java8.util.Optional;

/** Converts between Firestore nested objects and {@link AuditInfo} instances. */
class AuditInfoConverter {

  @NonNull
  static AuditInfo toAuditInfo(@NonNull AuditInfoNestedObject doc) throws DataStoreException {
    checkNotNull(doc.getClientTimestamp(), "clientTimestamp");
    return AuditInfo.builder()
        .setUser(UserConverter.toUser(doc.getUser()))
        .setClientTimestamp(doc.getClientTimestamp().toDate())
        .setServerTimestamp(Optional.ofNullable(doc.getServerTimestamp()).map(Timestamp::toDate))
        .build();
  }

  @NonNull
  static AuditInfoNestedObject fromMutationAndUser(Mutation mutation, User user) {
    return new AuditInfoNestedObject(
        UserConverter.toNestedObject(user), new Timestamp(mutation.getClientTimestamp()), null);
  }
}