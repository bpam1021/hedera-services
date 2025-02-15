package com.hedera.services.store.contracts.precompile.utils;

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

import com.hedera.services.store.contracts.precompile.proxy.RedirectTarget;
import org.apache.tuweni.bytes.Bytes;

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.services.utils.EntityIdUtils.tokenIdFromEvmAddress;

public class DescriptorUtils {
	public static boolean isTokenProxyRedirect(final Bytes input) {
		return ABI_ID_REDIRECT_FOR_TOKEN == input.getInt(0);
	}

	public static RedirectTarget getRedirectTarget(final Bytes input) {
		final var tokenAddress = input.slice(4, 20);
		final var tokenId = tokenIdFromEvmAddress(tokenAddress.toArrayUnsafe());
		final var nestedInput = input.slice(24);
		return new RedirectTarget(nestedInput.getInt(0), tokenId);
	}

	private DescriptorUtils() {
		throw new UnsupportedOperationException("Utility Class");
	}
}
