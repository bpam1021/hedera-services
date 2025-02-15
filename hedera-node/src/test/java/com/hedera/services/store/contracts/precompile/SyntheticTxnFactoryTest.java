package com.hedera.services.store.contracts.precompile;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.ledger.accounts.ContractCustomizer;
import com.hedera.services.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.services.store.contracts.precompile.codec.Association;
import com.hedera.services.store.contracts.precompile.codec.BurnWrapper;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.MintWrapper;
import com.hedera.services.store.contracts.precompile.codec.SetApprovalForAllWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenCreateWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenTransferWrapper;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.NodeStake;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.account;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createNonFungibleTokenCreateWrapperWithKeys;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createTokenCreateWrapperWithKeys;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fixedFee;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fractionalFee;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.payer;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.receiver;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.royaltyFee;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.token;
import static com.hedera.services.store.contracts.precompile.SyntheticTxnFactory.MOCK_INITCODE;
import static com.hedera.services.store.contracts.precompile.SyntheticTxnFactory.WEIBARS_TO_TINYBARS;
import static com.hedera.services.txns.crypto.AutoCreationLogic.AUTO_MEMO;
import static com.hedera.services.txns.crypto.AutoCreationLogic.THREE_MONTHS_IN_SECONDS;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SyntheticTxnFactoryTest {
	@Mock
	private EthTxData ethTxData;
	@Mock
	private ContractCustomizer customizer;
	@Mock
	private GlobalDynamicProperties dynamicProperties;

	private SyntheticTxnFactory subject;

	@BeforeEach
	void setUp() {
		subject = new SyntheticTxnFactory(dynamicProperties);
	}

	@Test
	void synthesizesPrecheckCallFromEthDataWithFnParams() {
		given(ethTxData.hasToAddress()).willReturn(true);
		given(ethTxData.hasCallData()).willReturn(true);
		given(ethTxData.callData()).willReturn(callData);
		given(ethTxData.gasLimit()).willReturn(gasLimit);
		given(ethTxData.value()).willReturn(value);
		given(ethTxData.to()).willReturn(addressTo);
		final var expectedId = ContractID.newBuilder()
				.setEvmAddress(ByteString.copyFrom(ethTxData.to()))
				.build();

		final var synthBody = subject.synthPrecheckContractOpFromEth(ethTxData);

		assertTrue(synthBody.hasContractCall());
		final var op = synthBody.getContractCall();
		assertArrayEquals(callData, op.getFunctionParameters().toByteArray());
		assertEquals(gasLimit, op.getGas());
		assertEquals(valueInTinyBars, op.getAmount());
		assertEquals(expectedId, op.getContractID());
	}

	@Test
	void synthesizesCallFromEthDataWithFnParams() {
		given(ethTxData.hasToAddress()).willReturn(true);
		given(ethTxData.hasCallData()).willReturn(true);
		given(ethTxData.callData()).willReturn(callData);
		given(ethTxData.gasLimit()).willReturn(gasLimit);
		given(ethTxData.value()).willReturn(value);
		given(ethTxData.to()).willReturn(addressTo);
		final var expectedId = ContractID.newBuilder()
				.setEvmAddress(ByteString.copyFrom(ethTxData.to()))
				.build();

		final var optSynthBody = subject.synthContractOpFromEth(ethTxData);
		assertTrue(optSynthBody.isPresent());
		final var synthBody = optSynthBody.get().build();

		assertTrue(synthBody.hasContractCall());
		final var op = synthBody.getContractCall();
		assertArrayEquals(callData, op.getFunctionParameters().toByteArray());
		assertEquals(gasLimit, op.getGas());
		assertEquals(valueInTinyBars, op.getAmount());
		assertEquals(expectedId, op.getContractID());
	}

	@Test
	void synthesizesCallFromEthDataWithNoFnParams() {
		given(ethTxData.hasToAddress()).willReturn(true);
		given(ethTxData.gasLimit()).willReturn(gasLimit);
		given(ethTxData.value()).willReturn(value);
		given(ethTxData.to()).willReturn(addressTo);
		final var expectedId = ContractID.newBuilder()
				.setEvmAddress(ByteString.copyFrom(ethTxData.to()))
				.build();

		final var optSynthBody = subject.synthContractOpFromEth(ethTxData);
		assertTrue(optSynthBody.isPresent());
		final var synthBody = optSynthBody.get().build();

		assertTrue(synthBody.hasContractCall());
		final var op = synthBody.getContractCall();
		assertTrue(op.getFunctionParameters().isEmpty());
		assertEquals(gasLimit, op.getGas());
		assertEquals(valueInTinyBars, op.getAmount());
		assertEquals(expectedId, op.getContractID());
	}

	@Test
	void requiresCreateFromEthDataToHaveInitcode() {
		final var optSynthBody = subject.synthContractOpFromEth(ethTxData);

		assertTrue(optSynthBody.isEmpty());
	}

	@Test
	void synthesizesCreateFromEthDataWithInitcode() {
		given(ethTxData.hasCallData()).willReturn(true);
		given(ethTxData.callData()).willReturn(callData);
		given(ethTxData.gasLimit()).willReturn(gasLimit);
		given(ethTxData.value()).willReturn(value);
		given(dynamicProperties.typedMinAutoRenewDuration()).willReturn(autoRenewPeriod);

		final var optSynthBody = subject.synthContractOpFromEth(ethTxData);

		assertTrue(optSynthBody.isPresent());
		final var synthBody = optSynthBody.get().build();

		assertTrue(synthBody.hasContractCreateInstance());
		final var op = synthBody.getContractCreateInstance();
		assertArrayEquals(callData, op.getInitcode().toByteArray());
		assertEquals(gasLimit, op.getGas());
		assertEquals(valueInTinyBars, op.getInitialBalance());
		assertEquals(autoRenewPeriod, op.getAutoRenewPeriod());
	}

	@Test
	void synthesizesPrecheckCreateFromEthDataWithInitcode() {
		given(ethTxData.hasCallData()).willReturn(true);
		given(ethTxData.callData()).willReturn(callData);
		given(ethTxData.gasLimit()).willReturn(gasLimit);
		given(ethTxData.value()).willReturn(value);
		given(dynamicProperties.typedMinAutoRenewDuration()).willReturn(autoRenewPeriod);

		final var synthBody = subject.synthPrecheckContractOpFromEth(ethTxData);

		assertTrue(synthBody.hasContractCreateInstance());
		final var op = synthBody.getContractCreateInstance();
		assertArrayEquals(callData, op.getInitcode().toByteArray());
		assertEquals(gasLimit, op.getGas());
		assertEquals(valueInTinyBars, op.getInitialBalance());
		assertEquals(autoRenewPeriod, op.getAutoRenewPeriod());
	}

	@Test
	void synthesizesPrecheckCreateFromEthDataWithoutInitcode() {
		given(ethTxData.gasLimit()).willReturn(gasLimit);
		given(ethTxData.value()).willReturn(value);
		given(dynamicProperties.typedMinAutoRenewDuration()).willReturn(autoRenewPeriod);
		given(ethTxData.replaceCallData(MOCK_INITCODE)).willReturn(ethTxData);
		given(ethTxData.callData()).willReturn(MOCK_INITCODE);

		final var synthBody = subject.synthPrecheckContractOpFromEth(ethTxData);

		assertTrue(synthBody.hasContractCreateInstance());
		final var op = synthBody.getContractCreateInstance();
		assertArrayEquals(SyntheticTxnFactory.MOCK_INITCODE, op.getInitcode().toByteArray());
		assertEquals(gasLimit, op.getGas());
		assertEquals(valueInTinyBars, op.getInitialBalance());
		assertEquals(autoRenewPeriod, op.getAutoRenewPeriod());
	}

	@Test
	void synthesizesExpectedContractAutoRenew() {
		final var result = subject.synthContractAutoRenew(
				contractNum, newExpiry, autoRenewAccountNum.toGrpcAccountId());
		final var synthBody = result.build();

		assertTrue(result.hasContractUpdateInstance());
		final var op = synthBody.getContractUpdateInstance();
		assertEquals(contractNum.toGrpcContractID(), op.getContractID());
		assertEquals(autoRenewAccountNum.toGrpcAccountId(), synthBody.getTransactionID().getAccountID());
		assertEquals(newExpiry, op.getExpirationTime().getSeconds());
	}

	@Test
	void synthesizesExpectedContractAutoRemove() {
		final var result = subject.synthContractAutoRemove(contractNum);
		final var synthBody = result.build();

		assertTrue(result.hasContractDeleteInstance());
		final var op = synthBody.getContractDeleteInstance();
		assertEquals(contractNum.toGrpcContractID(), op.getContractID());
	}

	@Test
	void synthesizesExpectedAccountAutoRemove() {
		final var result = subject.synthAccountAutoRemove(accountNum);
		final var synthBody = result.build();

		assertTrue(result.hasCryptoDelete());
		final var op = synthBody.getCryptoDelete();
		assertEquals(accountNum.toGrpcAccountId(), op.getDeleteAccountID());
	}

	@Test
	void synthesizesExpectedAccountAutoRenew() {
		final var result = subject.synthAccountAutoRenew(accountNum, newExpiry);
		final var synthBody = result.build();

		assertTrue(result.hasCryptoUpdateAccount());
		final var op = synthBody.getCryptoUpdateAccount();
		final var grpcId = accountNum.toGrpcAccountId();
		assertEquals(grpcId, op.getAccountIDToUpdate());
		assertEquals(grpcId, synthBody.getTransactionID().getAccountID());
		assertEquals(newExpiry, op.getExpirationTime().getSeconds());
	}

	@Test
	void createsExpectedContractSkeleton() {
		final var result = subject.contractCreation(customizer);
		verify(customizer).customizeSynthetic(any());
		assertTrue(result.hasContractCreateInstance());
		assertFalse(result.getContractCreateInstance().hasAutoRenewAccountId());
	}

	@Test
	void createsExpectedTransactionCall() {
		final var result = subject.createTransactionCall(1, Bytes.of(1));
		final var txnBody = result.build();

		assertTrue(result.hasContractCall());
		assertEquals(1, txnBody.getContractCall().getGas());
		assertEquals(EntityIdUtils.contractIdFromEvmAddress(
						Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()),
				txnBody.getContractCall().getContractID());
		assertEquals(ByteString.copyFrom(Bytes.of(1).toArray()), txnBody.getContractCall().getFunctionParameters());
	}

	@Test
	void createsExpectedCryptoCreate() {
		final var balance = 10L;
		final var alias = KeyFactory.getDefaultInstance().newEd25519();
		final var result = subject.createAccount(alias, balance);
		final var txnBody = result.build();

		assertTrue(txnBody.hasCryptoCreateAccount());
		assertEquals(AUTO_MEMO, txnBody.getCryptoCreateAccount().getMemo());
		assertEquals(THREE_MONTHS_IN_SECONDS,
				txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
		assertEquals(10L,
				txnBody.getCryptoCreateAccount().getInitialBalance());
		assertEquals(alias.toByteString(),
				txnBody.getCryptoCreateAccount().getKey().toByteString());
	}

	@Test
	void createsExpectedNodeStakeUpdate() {
		final var now = Instant.now();
		final var rewardRate = 10_000_000L;
		final var timestamp = Timestamp.newBuilder()
				.setSeconds(now.getEpochSecond())
				.setNanos(now.getNano())
				.build();
		final var nodeStakes = List.of(
				NodeStake.newBuilder()
						.setStake(123_456_789L)
						.setStakeRewarded(1_234_567L)
						.build(),
				NodeStake.newBuilder()
						.setStake(987_654_321L)
						.setStakeRewarded(54_321L)
						.build()
		);

		final var txnBody = subject.nodeStakeUpdate(timestamp, nodeStakes);

		assertTrue(txnBody.hasNodeStakeUpdate());
		assertEquals(timestamp, txnBody.getNodeStakeUpdate().getEndOfStakingPeriod());
		assertEquals(123_456_789L, txnBody.getNodeStakeUpdate().getNodeStake(0).getStake());
		assertEquals(1_234_567L, txnBody.getNodeStakeUpdate().getNodeStake(0).getStakeRewarded());
		assertEquals(987_654_321L, txnBody.getNodeStakeUpdate().getNodeStake(1).getStake());
		assertEquals(54_321L, txnBody.getNodeStakeUpdate().getNodeStake(1).getStakeRewarded());
	}

	@Test
	void createsExpectedAssociations() {
		final var tokens = List.of(fungible, nonFungible);
		final var associations = Association.multiAssociation(a, tokens);

		final var result = subject.createAssociate(associations);
		final var txnBody = result.build();

		assertEquals(a, txnBody.getTokenAssociate().getAccount());
		assertEquals(tokens, txnBody.getTokenAssociate().getTokensList());
	}

	@Test
	void createsExpectedDissociations() {
		final var tokens = List.of(fungible, nonFungible);
		final var associations = Dissociation.multiDissociation(a, tokens);

		final var result = subject.createDissociate(associations);
		final var txnBody = result.build();

		assertEquals(a, txnBody.getTokenDissociate().getAccount());
		assertEquals(tokens, txnBody.getTokenDissociate().getTokensList());
	}

	@Test
	void createsExpectedNftMint() {
		final var nftMints = MintWrapper.forNonFungible(nonFungible, newMetadata);

		final var result = subject.createMint(nftMints);
		final var txnBody = result.build();

		assertEquals(nonFungible, txnBody.getTokenMint().getToken());
		assertEquals(newMetadata, txnBody.getTokenMint().getMetadataList());
	}

	@Test
	void createsExpectedNftBurn() {
		final var nftBurns = BurnWrapper.forNonFungible(nonFungible, targetSerialNos);

		final var result = subject.createBurn(nftBurns);
		final var txnBody = result.build();

		assertEquals(nonFungible, txnBody.getTokenBurn().getToken());
		assertEquals(targetSerialNos, txnBody.getTokenBurn().getSerialNumbersList());
	}

	@Test
	void createsExpectedFungibleMint() {
		final var amount = 1234L;
		final var funMints = MintWrapper.forFungible(fungible, amount);

		final var result = subject.createMint(funMints);
		final var txnBody = result.build();

		assertEquals(fungible, txnBody.getTokenMint().getToken());
		assertEquals(amount, txnBody.getTokenMint().getAmount());
	}

	@Test
	void createsExpectedFungibleApproveAllowance() {
		final var amount = BigInteger.ONE;
		var allowances = new ApproveWrapper(token, receiver, amount, BigInteger.ZERO, true);

		final var result = subject.createFungibleApproval(allowances);
		final var txnBody = result.build();

		assertEquals(amount.longValue(), txnBody.getCryptoApproveAllowance().getTokenAllowances(0).getAmount());
		assertEquals(token, txnBody.getCryptoApproveAllowance().getTokenAllowances(0).getTokenId());
		assertEquals(receiver, txnBody.getCryptoApproveAllowance().getTokenAllowances(0).getSpender());
	}

	@Test
	void createsExpectedNonfungibleApproveAllowanceWithOwnerAsOperator() {
		var allowances = new ApproveWrapper(token, receiver, BigInteger.ZERO, BigInteger.ONE, false);
		final var ownerId = new EntityId(0, 0, 666);

		final var result = subject.createNonfungibleApproval(allowances, ownerId, ownerId);
		final var txnBody = result.build();

		final var allowance = txnBody.getCryptoApproveAllowance().getNftAllowances(0);
		assertEquals(token, allowance.getTokenId());
		assertEquals(receiver, allowance.getSpender());
		assertEquals(ownerId.toGrpcAccountId(), allowance.getOwner());
		assertEquals(AccountID.getDefaultInstance(), allowance.getDelegatingSpender());
		assertEquals(1L, allowance.getSerialNumbers(0));
	}

	@Test
	void createsExpectedNonfungibleApproveAllowanceWithNonOwnerOperator() {
		var allowances = new ApproveWrapper(token, receiver, BigInteger.ZERO, BigInteger.ONE, false);
		final var ownerId = new EntityId(0, 0, 666);
		final var operatorId = new EntityId(0, 0, 777);

		final var result = subject.createNonfungibleApproval(allowances, ownerId, operatorId);
		final var txnBody = result.build();

		final var allowance = txnBody.getCryptoApproveAllowance().getNftAllowances(0);
		assertEquals(token, allowance.getTokenId());
		assertEquals(receiver, allowance.getSpender());
		assertEquals(ownerId.toGrpcAccountId(), allowance.getOwner());
		assertEquals(operatorId.toGrpcAccountId(), allowance.getDelegatingSpender());
		assertEquals(1L, allowance.getSerialNumbers(0));
	}

	@Test
	void createsExpectedNonfungibleApproveAllowanceWithoutOwner() {
		var allowances = new ApproveWrapper(token, receiver, BigInteger.ZERO, BigInteger.ONE, false);
		final var operatorId = new EntityId(0, 0, 666);

		final var result = subject.createNonfungibleApproval(allowances, null, operatorId);
		final var txnBody = result.build();

		final var allowance = txnBody.getCryptoApproveAllowance().getNftAllowances(0);
		assertEquals(token, allowance.getTokenId());
		assertEquals(receiver, allowance.getSpender());
		assertEquals(AccountID.getDefaultInstance(), allowance.getOwner());
		assertEquals(1L, allowance.getSerialNumbers(0));
	}

	@Test
	void createsAdjustAllowanceForAllNFT() {
		var allowances = new SetApprovalForAllWrapper(receiver, true);

		final var result = subject.createApproveAllowanceForAllNFT(allowances, token);
		final var txnBody = result.build();

		assertEquals(receiver, txnBody.getCryptoApproveAllowance().getNftAllowances(0).getSpender());
		assertEquals(token, txnBody.getCryptoApproveAllowance().getNftAllowances(0).getTokenId());
		assertEquals(BoolValue.of(true), txnBody.getCryptoApproveAllowance().getNftAllowances(0).getApprovedForAll());
	}

	@Test
	void createsDeleteAllowance() {
		var allowances = new ApproveWrapper(token, receiver, BigInteger.ZERO, BigInteger.ONE, false);

		final var result = subject.createDeleteAllowance(allowances, senderId);
		final var txnBody = result.build();

		assertEquals(token, txnBody.getCryptoDeleteAllowance().getNftAllowances(0).getTokenId());
		assertEquals(1L, txnBody.getCryptoDeleteAllowance().getNftAllowances(0).getSerialNumbers(0));
		assertEquals(sender, txnBody.getCryptoDeleteAllowance().getNftAllowances(0).getOwner());
	}

	@Test
	void createsExpectedFungibleBurn() {
		final var amount = 1234L;
		final var funBurns = BurnWrapper.forFungible(fungible, amount);

		final var result = subject.createBurn(funBurns);
		final var txnBody = result.build();

		assertEquals(fungible, txnBody.getTokenBurn().getToken());
		assertEquals(amount, txnBody.getTokenBurn().getAmount());
	}

	@Test
	void createsExpectedFungibleTokenCreate() {
		// given
		final var adminKey = new TokenCreateWrapper.KeyValueWrapper(
				false, null, new byte[] { }, new byte[] { }, EntityIdUtils.contractIdFromEvmAddress(contractAddress)
		);
		final var multiKey = new TokenCreateWrapper.KeyValueWrapper(
				false, EntityIdUtils.contractIdFromEvmAddress(contractAddress), new byte[] { }, new byte[] { }, null
		);
		final var wrapper = createTokenCreateWrapperWithKeys(List.of(
				new TokenCreateWrapper.TokenKeyWrapper(254, multiKey),
				new TokenCreateWrapper.TokenKeyWrapper(1, adminKey))
		);
		wrapper.setFixedFees(List.of(fixedFee));
		wrapper.setFractionalFees(List.of(fractionalFee));

		// when
		final var result = subject.createTokenCreate(wrapper);
		final var txnBody = result.build().getTokenCreation();

		// then
		assertTrue(result.hasTokenCreation());

		assertEquals(TokenType.FUNGIBLE_COMMON, txnBody.getTokenType());
		assertEquals("token", txnBody.getName());
		assertEquals("symbol", txnBody.getSymbol());
		assertEquals(account, txnBody.getTreasury());
		assertEquals("memo", txnBody.getMemo());
		assertEquals(TokenSupplyType.INFINITE, txnBody.getSupplyType());
		assertEquals(Long.MAX_VALUE, txnBody.getInitialSupply());
		assertEquals(Integer.MAX_VALUE, txnBody.getDecimals());
		assertEquals(5054L, txnBody.getMaxSupply());
		assertFalse(txnBody.getFreezeDefault());
		assertEquals(442L, txnBody.getExpiry().getSeconds());
		assertEquals(555L, txnBody.getAutoRenewPeriod().getSeconds());
		assertEquals(payer, txnBody.getAutoRenewAccount());

		// keys assertions
		assertTrue(txnBody.hasAdminKey());
		assertEquals(adminKey.asGrpc(), txnBody.getAdminKey());
		assertTrue(txnBody.hasKycKey());
		assertEquals(multiKey.asGrpc(), txnBody.getKycKey());
		assertTrue(txnBody.hasFreezeKey());
		assertEquals(multiKey.asGrpc(), txnBody.getFreezeKey());
		assertTrue(txnBody.hasWipeKey());
		assertEquals(multiKey.asGrpc(), txnBody.getWipeKey());


		// assert custom fees
		assertEquals(2, txnBody.getCustomFeesCount());
		assertEquals(fixedFee.asGrpc(), txnBody.getCustomFees(0));
		assertEquals(fractionalFee.asGrpc(), txnBody.getCustomFees(1));
	}

	@Test
	void createsExpectedNonFungibleTokenCreate() {
		// given
		final var multiKey = new TokenCreateWrapper.KeyValueWrapper(
				false, EntityIdUtils.contractIdFromEvmAddress(contractAddress), new byte[] { }, new byte[] { }, null
		);
		final var wrapper = createNonFungibleTokenCreateWrapperWithKeys(List.of(
				new TokenCreateWrapper.TokenKeyWrapper(112, multiKey))
		);
		wrapper.setFixedFees(List.of(fixedFee));
		wrapper.setRoyaltyFees(List.of(royaltyFee));

		// when
		final var result = subject.createTokenCreate(wrapper);
		final var txnBody = result.build().getTokenCreation();

		// then
		assertTrue(result.hasTokenCreation());

		assertEquals(TokenType.NON_FUNGIBLE_UNIQUE, txnBody.getTokenType());
		assertEquals("nft", txnBody.getName());
		assertEquals("NFT", txnBody.getSymbol());
		assertEquals(account, txnBody.getTreasury());
		assertEquals("nftMemo", txnBody.getMemo());
		assertEquals(TokenSupplyType.FINITE, txnBody.getSupplyType());
		assertEquals(0L, txnBody.getInitialSupply());
		assertEquals(0, txnBody.getDecimals());
		assertEquals(5054L, txnBody.getMaxSupply());
		assertTrue(txnBody.getFreezeDefault());
		assertEquals(0, txnBody.getExpiry().getSeconds());
		assertEquals(0, txnBody.getAutoRenewPeriod().getSeconds());
		assertFalse(txnBody.hasAutoRenewAccount());

		// keys assertions
		assertTrue(txnBody.hasSupplyKey());
		assertEquals(multiKey.asGrpc(), txnBody.getSupplyKey());
		assertTrue(txnBody.hasFeeScheduleKey());
		assertEquals(multiKey.asGrpc(), txnBody.getFeeScheduleKey());
		assertTrue(txnBody.hasPauseKey());
		assertEquals(multiKey.asGrpc(), txnBody.getPauseKey());

		// assert custom fees
		assertEquals(2, txnBody.getCustomFeesCount());
		assertEquals(fixedFee.asGrpc(), txnBody.getCustomFees(0));
		assertEquals(royaltyFee.asGrpc(), txnBody.getCustomFees(1));
	}

	@Test
	void createsExpectedCryptoTransfer() {
		final var fungibleTransfer = new SyntheticTxnFactory.FungibleTokenTransfer(secondAmount, false, fungible, b, a);

		final var result = subject.createCryptoTransfer(
				List.of(new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer))));
		final var txnBody = result.build();

		final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
		final var expFungibleTransfer = tokenTransfers.get(0);
		assertEquals(fungible, expFungibleTransfer.getToken());
		assertEquals(
				List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
				expFungibleTransfer.getTransfersList());
	}

	@Test
	void acceptsEmptyWrappers() {
		final var result = subject.createCryptoTransfer(List.of());

		final var txnBody = result.build();
		assertEquals(0, txnBody.getCryptoTransfer().getTokenTransfersCount());
	}

	@Test
	void canCreateApprovedNftExchanges() {
		final var approvedExchange = SyntheticTxnFactory.NftExchange.fromApproval(
				1L, nonFungible, a, b);
		assertTrue(approvedExchange.isApproval());
	}

	@Test
	void mergesRepeatedTokenIds() {
		final var fungibleTransfer = new SyntheticTxnFactory.FungibleTokenTransfer(secondAmount, false, fungible, b, a);
		final var nonFungibleTransfer = new SyntheticTxnFactory.NftExchange(1L, nonFungible, a, b);
		assertFalse(nonFungibleTransfer.isApproval());

		final var result = subject.createCryptoTransfer(
				List.of(
						new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer)),
						new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer)),
						new TokenTransferWrapper(List.of(nonFungibleTransfer), Collections.emptyList())));

		final var txnBody = result.build();

		final var finalTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
		assertEquals(2, finalTransfers.size());
		final var mergedFungible = finalTransfers.get(0);
		assertEquals(fungible, mergedFungible.getToken());
		assertEquals(
				List.of(
						aaWith(b, -2 * secondAmount),
						aaWith(a, +2 * secondAmount)),
				mergedFungible.getTransfersList());
	}

	@Test
	void createsExpectedCryptoTransferForNFTTransfer() {
		final var nftExchange = new SyntheticTxnFactory.NftExchange(serialNo, nonFungible, a, c);

		final var result = subject.createCryptoTransfer(Collections.singletonList(new TokenTransferWrapper(
				List.of(nftExchange),
				Collections.emptyList())));
		final var txnBody = result.build();

		final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
		final var expNftTransfer = tokenTransfers.get(0);
		assertEquals(nonFungible, expNftTransfer.getToken());
		assertEquals(List.of(nftExchange.asGrpc()), expNftTransfer.getNftTransfersList());
		assertEquals(1, tokenTransfers.size());
	}

	@Test
	void createsExpectedCryptoTransferForFungibleTransfer() {
		final var fungibleTransfer = new SyntheticTxnFactory.FungibleTokenTransfer(secondAmount, false, fungible, b, a);

		final var result = subject.createCryptoTransfer(Collections.singletonList(new TokenTransferWrapper(
				Collections.emptyList(),
				List.of(fungibleTransfer))));
		final var txnBody = result.build();

		final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
		final var expFungibleTransfer = tokenTransfers.get(0);
		assertEquals(fungible, expFungibleTransfer.getToken());
		assertEquals(
				List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
				expFungibleTransfer.getTransfersList());
		assertEquals(1, tokenTransfers.size());
	}

	@Test
	void createsExpectedCryptoTransfersForMultipleTransferWrappers() {
		final var nftExchange = new SyntheticTxnFactory.NftExchange(serialNo, nonFungible, a, c);
		final var fungibleTransfer = new SyntheticTxnFactory.FungibleTokenTransfer(secondAmount,false,  fungible, b, a);

		final var result = subject.createCryptoTransfer(
				List.of(
						new TokenTransferWrapper(
								Collections.emptyList(),
								List.of(fungibleTransfer)),
						new TokenTransferWrapper(
								List.of(nftExchange),
								Collections.emptyList())));
		final var txnBody = result.build();

		final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();

		final var expFungibleTransfer = tokenTransfers.get(0);
		assertEquals(fungible, expFungibleTransfer.getToken());
		assertEquals(
				List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
				expFungibleTransfer.getTransfersList());

		final var expNftTransfer = tokenTransfers.get(1);
		assertEquals(nonFungible, expNftTransfer.getToken());
		assertEquals(List.of(nftExchange.asGrpc()), expNftTransfer.getNftTransfersList());
	}

	@Test
	void mergesFungibleTransfersAsExpected() {
		final var source = new TokenTransferWrapper(
				Collections.emptyList(),
				List.of(
						new SyntheticTxnFactory.FungibleTokenTransfer(1, false, fungible, a, b)
				)).asGrpcBuilder();
		final var target = new TokenTransferWrapper(
				Collections.emptyList(),
				List.of(
						new SyntheticTxnFactory.FungibleTokenTransfer(2, false, fungible, b, c)
				)).asGrpcBuilder();

		SyntheticTxnFactory.mergeTokenTransfers(target, source);

		assertEquals(fungible, target.getToken());
		final var transfers = target.getTransfersList();
		assertEquals(List.of(
				aaWith(b, -1),
				aaWith(c, +2),
				aaWith(a, -1)
		), transfers);
	}

	@Test
	void mergesNftExchangesAsExpected() {
		final var repeatedExchange = new SyntheticTxnFactory.NftExchange(1L, nonFungible, a, b);
		final var newExchange = new SyntheticTxnFactory.NftExchange(2L, nonFungible, a, b);
		final var source = new TokenTransferWrapper(
				List.of(repeatedExchange, newExchange),
				Collections.emptyList()
		).asGrpcBuilder();
		final var target = new TokenTransferWrapper(
				List.of(repeatedExchange),
				Collections.emptyList()
		).asGrpcBuilder();

		SyntheticTxnFactory.mergeTokenTransfers(target, source);

		assertEquals(nonFungible, target.getToken());
		final var transfers = target.getNftTransfersList();
		assertEquals(List.of(
				repeatedExchange.asGrpc(),
				newExchange.asGrpc()
		), transfers);
	}

	@Test
	void distinguishesDifferentExchangeBuilders() {
		final var subject = new SyntheticTxnFactory.NftExchange(1L, nonFungible, a, b)
				.asGrpc().toBuilder();

		final var differentSerialNo = new SyntheticTxnFactory.NftExchange(2L, nonFungible, a, b);
		final var differentSender = new SyntheticTxnFactory.NftExchange(1L, nonFungible, c, b);
		final var differentReceiver = new SyntheticTxnFactory.NftExchange(1L, nonFungible, a, c);

		assertFalse(SyntheticTxnFactory.areSameBuilder(subject, differentSerialNo.asGrpc().toBuilder()));
		assertFalse(SyntheticTxnFactory.areSameBuilder(subject, differentReceiver.asGrpc().toBuilder()));
		assertFalse(SyntheticTxnFactory.areSameBuilder(subject, differentSender.asGrpc().toBuilder()));
	}

	private AccountAmount aaWith(final AccountID account, final long amount) {
		return AccountAmount.newBuilder()
				.setAccountID(account)
				.setAmount(amount)
				.build();
	}

	private static final long serialNo = 100;
	private static final long secondAmount = 200;
	private static final long newExpiry = 1_234_567L;
	private final EntityNum contractNum = EntityNum.fromLong(666);
	private final EntityNum accountNum = EntityNum.fromLong(1234);
	private final EntityNum autoRenewAccountNum = EntityNum.fromLong(999);
	private static final AccountID a = IdUtils.asAccount("0.0.2");
	private static final AccountID b = IdUtils.asAccount("0.0.3");
	private static final AccountID c = IdUtils.asAccount("0.0.4");
	private static final TokenID fungible = IdUtils.asToken("0.0.555");
	private static final TokenID nonFungible = IdUtils.asToken("0.0.666");
	private static final List<Long> targetSerialNos = List.of(1L, 2L, 3L);
	private static final List<ByteString> newMetadata = List.of(
			ByteString.copyFromUtf8("AAA"), ByteString.copyFromUtf8("BBB"), ByteString.copyFromUtf8("CCC"));
	private static final long valueInTinyBars = 123;
	private static final BigInteger value = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(valueInTinyBars));
	private static final long gasLimit = 123;
	private static final byte[] callData = "Between the idea and the reality".getBytes();
	private static final byte[] addressTo = unhex("abcdefabcdefabcdefbabcdefabcdefabcdefbbb");
	private static final Duration autoRenewPeriod = Duration.newBuilder().setSeconds(1_234_567).build();
}
