package com.hedera.services.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.swirlds.common.io.streams.SerializableDataInputStream;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SerializationUtilsTest {
	@Test
	void deserializesEmptyFungibleAllowancesAsSingletonEmptyMap() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		assertSame(Collections.emptyMap(), SerializationUtils.deserializeFungibleTokenAllowances(in));
	}

	@Test
	void deserializesEmptyCryptoAllowancesAsSingletonEmptyMap() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		assertSame(Collections.emptyMap(), SerializationUtils.deserializeCryptoAllowances(in));
	}

	@Test
	void deserializesEmptyNftAllowancesAsSingletonEmptyMap() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		assertSame(Collections.emptySet(), SerializationUtils.deserializeApproveForAllNftsAllowances(in));
	}
}
