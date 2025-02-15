package com.hedera.services.bdd.suites.utils;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.swirlds.common.stream.StreamType;

public class RecordStreamType implements StreamType {
	int RECORD_VERSION = 5;
	String RECORD_DESCRIPTION = "records";
	String RECORD_EXTENSION = "rcd";
	String RECORD_SIG_EXTENSION = "rcd_sig";

	@Override
	public int[] getFileHeader() {
		return new int[0];
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getDescription() {
		return RECORD_DESCRIPTION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getExtension() {
		return RECORD_EXTENSION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getSigExtension() {
		return RECORD_SIG_EXTENSION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] getSigFileHeader() {
		return new byte[] { (byte) RECORD_VERSION };
	}
}
