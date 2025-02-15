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
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.services.store.contracts.precompile.codec.Association;
import com.hedera.services.store.contracts.precompile.codec.BurnWrapper;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.MintWrapper;
import com.hedera.services.store.contracts.precompile.codec.SetApprovalForAllWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenCreateWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenTransferWrapper;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.NodeStake;
import com.hederahashgraph.api.proto.java.NodeStakeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.tuweni.bytes.Bytes;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILE_MIRROR_ID;
import static com.hedera.services.txns.crypto.AutoCreationLogic.AUTO_MEMO;
import static com.hedera.services.txns.crypto.AutoCreationLogic.THREE_MONTHS_IN_SECONDS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

@Singleton
public class SyntheticTxnFactory {
	protected static final byte[] MOCK_INITCODE = new byte[32];
	public static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);

	private final GlobalDynamicProperties dynamicProperties;

	@Inject
	public SyntheticTxnFactory(final GlobalDynamicProperties dynamicProperties) {
		this.dynamicProperties = dynamicProperties;
	}

	/**
	 * Given an instance of {@link EthTxData} populated from a raw Ethereum transaction, synthesizes a
	 * {@link TransactionBody} for use during precheck. In the case of a {@code ContractCreate}, if the
	 * call data is missing, replaces it with dummy initcode (it is not the job of precheck to look up
	 * initcode from a file).
	 *
	 * @param ethTxData
	 * 		the Ethereum transaction data available in precheck
	 * @return the pre-checkable HAPI transaction
	 */
	public TransactionBody synthPrecheckContractOpFromEth(EthTxData ethTxData) {
		if (ethTxData.hasToAddress()) {
			return synthCallOpFromEth(ethTxData).build();
		} else {
			if (!ethTxData.hasCallData()) {
				ethTxData = ethTxData.replaceCallData(MOCK_INITCODE);
			}
			return synthCreateOpFromEth(ethTxData).build();
		}
	}

	/**
	 * Given an instance of {@link EthTxData} populated from a raw Ethereum transaction, tries to
	 * synthesize a builder for an appropriate HAPI TransactionBody---ContractCall if the given
	 * {@code ethTxData} has a "to" address, and ContractCreate otherwise.
	 *
	 * @param ethTxData
	 * 		the populated Ethereum transaction data
	 * @return an optional of the HAPI transaction builder if it could be synthesized
	 */
	public Optional<TransactionBody.Builder> synthContractOpFromEth(final EthTxData ethTxData) {
		if (ethTxData.hasToAddress()) {
			return Optional.of(synthCallOpFromEth(ethTxData));
		} else {
			// We can only synthesize a ContractCreate given initcode populated into the EthTxData callData field
			if (!ethTxData.hasCallData()) {
				return Optional.empty();
			}
			return Optional.of(synthCreateOpFromEth(ethTxData));
		}
	}

	public TransactionBody.Builder synthContractAutoRemove(final EntityNum contractNum) {
		final var op = ContractDeleteTransactionBody.newBuilder()
				.setContractID(contractNum.toGrpcContractID());
		return TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(contractNum.toGrpcAccountId()))
				.setContractDeleteInstance(op);
	}

	public TransactionBody.Builder synthAccountAutoRemove(final EntityNum accountNum) {
		final var grpcId = accountNum.toGrpcAccountId();
		final var op = CryptoDeleteTransactionBody.newBuilder()
				.setDeleteAccountID(grpcId);
		return TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(grpcId))
				.setCryptoDelete(op);
	}

	public TransactionBody.Builder synthContractAutoRenew(final EntityNum contractNum, final long newExpiry,
														  final AccountID payerForAutoRenew) {
		final var op = ContractUpdateTransactionBody.newBuilder()
				.setContractID(contractNum.toGrpcContractID())
				.setExpirationTime(MiscUtils.asSecondsTimestamp(newExpiry));
		return TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(payerForAutoRenew))
				.setContractUpdateInstance(op);
	}

	public TransactionBody.Builder synthAccountAutoRenew(final EntityNum accountNum, final long newExpiry) {
		final var grpcId = accountNum.toGrpcAccountId();
		final var op = CryptoUpdateTransactionBody.newBuilder()
				.setAccountIDToUpdate(grpcId)
				.setExpirationTime(MiscUtils.asSecondsTimestamp(newExpiry));
		return TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(grpcId))
				.setCryptoUpdateAccount(op);
	}

	public TransactionBody.Builder contractCreation(final ContractCustomizer customizer) {
		final var builder = ContractCreateTransactionBody.newBuilder();

		customizer.customizeSynthetic(builder);

		return TransactionBody.newBuilder().setContractCreateInstance(builder);
	}

	public TransactionBody.Builder createTransactionCall(long gas, Bytes functionParameters) {
		final var builder = ContractCallTransactionBody.newBuilder();

		builder.setContractID(HTS_PRECOMPILE_MIRROR_ID);
		builder.setGas(gas);
		builder.setFunctionParameters(ByteString.copyFrom(functionParameters.toArray()));

		return TransactionBody.newBuilder().setContractCall(builder);
	}

	public TransactionBody.Builder createBurn(final BurnWrapper burnWrapper) {
		final var builder = TokenBurnTransactionBody.newBuilder();

		builder.setToken(burnWrapper.tokenType());
		if (burnWrapper.type() == NON_FUNGIBLE_UNIQUE) {
			builder.addAllSerialNumbers(burnWrapper.serialNos());
		} else {
			builder.setAmount(burnWrapper.amount());
		}

		return TransactionBody.newBuilder().setTokenBurn(builder);
	}

	public TransactionBody.Builder createMint(final MintWrapper mintWrapper) {
		final var builder = TokenMintTransactionBody.newBuilder();

		builder.setToken(mintWrapper.tokenType());
		if (mintWrapper.type() == NON_FUNGIBLE_UNIQUE) {
			builder.addAllMetadata(mintWrapper.metadata());
		} else {
			builder.setAmount(mintWrapper.amount());
		}

		return TransactionBody.newBuilder().setTokenMint(builder);
	}

	public TransactionBody.Builder createFungibleApproval(final ApproveWrapper approveWrapper) {
		return createNonfungibleApproval(approveWrapper, null, null);
	}

	public TransactionBody.Builder createNonfungibleApproval(
			final ApproveWrapper approveWrapper,
			@Nullable final EntityId ownerId,
			@Nullable final EntityId operatorId
	) {
		final var builder = CryptoApproveAllowanceTransactionBody.newBuilder();
		if (approveWrapper.isFungible()) {
			builder.addTokenAllowances(TokenAllowance.newBuilder()
					.setTokenId(approveWrapper.token())
					.setSpender(approveWrapper.spender())
					.setAmount(approveWrapper.amount().longValue())
					.build());
		} else {
			final var op = NftAllowance.newBuilder()
							.setTokenId(approveWrapper.token())
							.setSpender(approveWrapper.spender())
							.addSerialNumbers(approveWrapper.serialNumber().longValue());
			if (ownerId != null) {
				op.setOwner(ownerId.toGrpcAccountId());
				if (!ownerId.equals(operatorId)) {
					op.setDelegatingSpender(Objects.requireNonNull(operatorId).toGrpcAccountId());
				}
			}
			builder.addNftAllowances(op.build());
		}
		return TransactionBody.newBuilder().setCryptoApproveAllowance(builder);
	}

	public TransactionBody.Builder createDeleteAllowance(final ApproveWrapper approveWrapper, final EntityId owner) {
		final var builder = CryptoDeleteAllowanceTransactionBody.newBuilder();
		builder.addAllNftAllowances(List.of(NftRemoveAllowance.newBuilder().setOwner(owner.toGrpcAccountId())
						.setTokenId(approveWrapper.token())
						.addAllSerialNumbers(List.of(approveWrapper.serialNumber().longValue()))
						.build()))
				.build();
		return TransactionBody.newBuilder().setCryptoDeleteAllowance(builder);
	}

	public TransactionBody.Builder createApproveAllowanceForAllNFT(
			final SetApprovalForAllWrapper setApprovalForAllWrapper,
			final TokenID tokenID
	) {
		final var builder = CryptoApproveAllowanceTransactionBody.newBuilder();

		builder.addNftAllowances(NftAllowance.newBuilder()
				.setApprovedForAll(BoolValue.of(setApprovalForAllWrapper.approved()))
				.setTokenId(tokenID)
				.setSpender(setApprovalForAllWrapper.to())
				.build());

		return TransactionBody.newBuilder().setCryptoApproveAllowance(builder);
	}

	/**
	 * Given a list of {@link TokenTransferWrapper}s, where each wrapper gives changes scoped to a particular
	 * {@link TokenID}, returns a synthetic {@code CryptoTransfer} whose {@link CryptoTransferTransactionBody}
	 * consolidates the wrappers.
	 * <p>
	 * If two wrappers both refer to the same token, their transfer lists are merged as specified in the
	 * {@link SyntheticTxnFactory#mergeTokenTransfers(TokenTransferList.Builder, TokenTransferList.Builder)}
	 * helper method.
	 *
	 * @param wrappers
	 * 		the wrappers to consolidate in a synthetic transaction
	 * @return the synthetic transaction
	 */
	public TransactionBody.Builder createCryptoTransfer(final List<TokenTransferWrapper> wrappers) {
		final var opBuilder = CryptoTransferTransactionBody.newBuilder();
		if (wrappers.size() == 1) {
			opBuilder.addTokenTransfers(wrappers.get(0).asGrpcBuilder());
		} else if (wrappers.size() > 1) {
			final List<TokenTransferList.Builder> builders = new ArrayList<>();
			final Map<TokenID, TokenTransferList.Builder> listBuilders = new HashMap<>();
			for (final TokenTransferWrapper wrapper : wrappers) {
				final var builder = wrapper.asGrpcBuilder();
				final var merged = listBuilders.merge(
						builder.getToken(), builder, SyntheticTxnFactory::mergeTokenTransfers);
				/* If merge() returns a builder other than the one we just created, it is already in the list */
				if (merged == builder) {
					builders.add(builder);
				}
			}
			builders.forEach(opBuilder::addTokenTransfers);
		}
		return TransactionBody.newBuilder().setCryptoTransfer(opBuilder);
	}

	public TransactionBody.Builder createAssociate(final Association association) {
		final var builder = TokenAssociateTransactionBody.newBuilder();

		builder.setAccount(association.accountId());
		builder.addAllTokens(association.tokenIds());

		return TransactionBody.newBuilder().setTokenAssociate(builder);
	}

	public TransactionBody.Builder createDissociate(final Dissociation dissociation) {
		final var builder = TokenDissociateTransactionBody.newBuilder();

		builder.setAccount(dissociation.accountId());
		builder.addAllTokens(dissociation.tokenIds());

		return TransactionBody.newBuilder().setTokenDissociate(builder);
	}

	public TransactionBody.Builder createTokenCreate(final TokenCreateWrapper tokenCreateWrapper) {
		final var txnBodyBuilder = TokenCreateTransactionBody.newBuilder();
		txnBodyBuilder.setName(tokenCreateWrapper.getName());
		txnBodyBuilder.setSymbol(tokenCreateWrapper.getSymbol());
		txnBodyBuilder.setDecimals(tokenCreateWrapper.getDecimals().intValue());
		txnBodyBuilder.setTokenType(tokenCreateWrapper.isFungible() ? TokenType.FUNGIBLE_COMMON : NON_FUNGIBLE_UNIQUE);
		txnBodyBuilder.setSupplyType(
				tokenCreateWrapper.isSupplyTypeFinite() ? TokenSupplyType.FINITE : TokenSupplyType.INFINITE);
		txnBodyBuilder.setMaxSupply(tokenCreateWrapper.getMaxSupply());
		txnBodyBuilder.setInitialSupply(tokenCreateWrapper.getInitSupply().longValue());
		if (tokenCreateWrapper.getTreasury() != null)
			txnBodyBuilder.setTreasury(tokenCreateWrapper.getTreasury());
		txnBodyBuilder.setFreezeDefault(tokenCreateWrapper.isFreezeDefault());
		txnBodyBuilder.setMemo(tokenCreateWrapper.getMemo());
		if (tokenCreateWrapper.getExpiry().second() != 0)
			txnBodyBuilder.setExpiry(Timestamp.newBuilder().setSeconds(tokenCreateWrapper.getExpiry().second()).build());
		if (tokenCreateWrapper.getExpiry().autoRenewAccount() != null)
			txnBodyBuilder.setAutoRenewAccount(tokenCreateWrapper.getExpiry().autoRenewAccount());
		if (tokenCreateWrapper.getExpiry().autoRenewPeriod() != 0)
			txnBodyBuilder.setAutoRenewPeriod(
					Duration.newBuilder().setSeconds(tokenCreateWrapper.getExpiry().autoRenewPeriod()));
		tokenCreateWrapper.getTokenKeys().forEach(tokenKeyWrapper -> {
			final var key = tokenKeyWrapper.key().asGrpc();
			if (tokenKeyWrapper.isUsedForAdminKey()) txnBodyBuilder.setAdminKey(key);
			if (tokenKeyWrapper.isUsedForKycKey()) txnBodyBuilder.setKycKey(key);
			if (tokenKeyWrapper.isUsedForFreezeKey()) txnBodyBuilder.setFreezeKey(key);
			if (tokenKeyWrapper.isUsedForWipeKey()) txnBodyBuilder.setWipeKey(key);
			if (tokenKeyWrapper.isUsedForSupplyKey()) txnBodyBuilder.setSupplyKey(key);
			if (tokenKeyWrapper.isUsedForFeeScheduleKey()) txnBodyBuilder.setFeeScheduleKey(key);
			if (tokenKeyWrapper.isUsedForPauseKey()) txnBodyBuilder.setPauseKey(key);
		});
		txnBodyBuilder.addAllCustomFees(tokenCreateWrapper.getFixedFees().stream()
				.map(TokenCreateWrapper.FixedFeeWrapper::asGrpc)
				.toList());
		txnBodyBuilder.addAllCustomFees(tokenCreateWrapper.getFractionalFees().stream()
				.map(TokenCreateWrapper.FractionalFeeWrapper::asGrpc)
				.toList());
		txnBodyBuilder.addAllCustomFees(tokenCreateWrapper.getRoyaltyFees().stream()
				.map(TokenCreateWrapper.RoyaltyFeeWrapper::asGrpc)
				.toList());
		return TransactionBody.newBuilder().setTokenCreation(txnBodyBuilder);
	}

	public TransactionBody.Builder createAccount(final Key alias, final long balance) {
		final var txnBody = CryptoCreateTransactionBody.newBuilder()
				.setKey(alias)
				.setMemo(AUTO_MEMO)
				.setInitialBalance(balance)
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(THREE_MONTHS_IN_SECONDS))
				.build();
		return TransactionBody.newBuilder().setCryptoCreateAccount(txnBody);
	}

	public TransactionBody.Builder nodeStakeUpdate(
			final Timestamp stakingPeriodEnd,
			final List<NodeStake> nodeStakes
	) {
		final var txnBody = NodeStakeUpdateTransactionBody.newBuilder()
				.setEndOfStakingPeriod(stakingPeriodEnd)
				.addAllNodeStake(nodeStakes)
				.build();

		return TransactionBody.newBuilder().setNodeStakeUpdate(txnBody);
	}

	public static class HbarTransfer {
		protected final long amount;
		protected final AccountID sender;
		protected final AccountID receiver;
		protected final boolean isApproval;

		public HbarTransfer(long amount, boolean isApproval, AccountID sender, AccountID receiver) {
			this.amount = amount;
			this.isApproval = isApproval;
			this.sender = sender;
			this.receiver = receiver;
		}

		public AccountAmount senderAdjustment() {
			return AccountAmount.newBuilder().setAccountID(sender).setAmount(-amount).setIsApproval(isApproval).build();
		}

		public AccountAmount receiverAdjustment() {
			return AccountAmount.newBuilder().setAccountID(receiver).setAmount(+amount).setIsApproval(
					isApproval).build();
		}

		public AccountID sender() {
			return sender;
		}

		public AccountID receiver() {
			return receiver;
		}

		public long amount() {
			return amount;
		}

		public boolean isApproval() {
			return isApproval;
		}
	}

	public static class FungibleTokenTransfer extends HbarTransfer {
		private final TokenID denomination;

		public FungibleTokenTransfer(
				long amount,
				boolean isApproval,
				TokenID denomination,
				AccountID sender,
				AccountID receiver
		) {
			super(amount, isApproval, sender, receiver);
			this.denomination = denomination;
		}

		public TokenID getDenomination() {
			return denomination;
		}
	}

	public static class NftExchange {
		private final long serialNo;

		private final TokenID tokenType;
		private final AccountID sender;
		private final AccountID receiver;
		private final boolean isApproval;

		public NftExchange(
				final long serialNo,
				final TokenID tokenType,
				final AccountID sender,
				final AccountID receiver
		) {
			this(serialNo, tokenType, sender, receiver, false);
		}

		public static NftExchange fromApproval(
				final long serialNo,
				final TokenID tokenType,
				final AccountID sender,
				final AccountID receiver
		) {
			return new NftExchange(serialNo, tokenType, sender, receiver, true);
		}

		private NftExchange(
				final long serialNo,
				final TokenID tokenType,
				final AccountID sender,
				final AccountID receiver,
				final boolean isApproval
		) {
			this.serialNo = serialNo;
			this.tokenType = tokenType;
			this.sender = sender;
			this.receiver = receiver;
			this.isApproval = isApproval;
		}

		public NftTransfer asGrpc() {
			return NftTransfer.newBuilder()
					.setSenderAccountID(sender)
					.setReceiverAccountID(receiver)
					.setSerialNumber(serialNo)
					.setIsApproval(isApproval)
					.build();
		}

		public TokenID getTokenType() {
			return tokenType;
		}

		public long getSerialNo() {
			return serialNo;
		}

		public boolean isApproval() {
			return isApproval;
		}
	}

	/**
	 * Merges the fungible and non-fungible transfers from one token transfer list into another. (Of course,
	 * at most one of these merges can be sensible; a token cannot be both fungible _and_ non-fungible.)
	 * <p>
	 * Fungible transfers are "merged" by summing up all the amount fields for each unique account id that
	 * appears in either list.  NFT exchanges are "merged" by checking that each exchange from either list
	 * appears at most once.
	 *
	 * @param to
	 * 		the builder to merge source transfers into
	 * @param from
	 * 		a source of fungible transfers and NFT exchanges
	 * @return the consolidated target builder
	 */
	static TokenTransferList.Builder mergeTokenTransfers(
			final TokenTransferList.Builder to,
			final TokenTransferList.Builder from
	) {
		mergeFungible(from, to);
		mergeNonFungible(from, to);
		return to;
	}

	private static void mergeFungible(final TokenTransferList.Builder from, final TokenTransferList.Builder to) {
		for (int i = 0, n = from.getTransfersCount(); i < n; i++) {
			final var transfer = from.getTransfers(i);
			final var targetId = transfer.getAccountID();
			var merged = false;
			for (int j = 0, m = to.getTransfersCount(); j < m; j++) {
				final var transferBuilder = to.getTransfersBuilder(j);
				if (targetId.equals(transferBuilder.getAccountID())) {
					final var prevAmount = transferBuilder.getAmount();
					transferBuilder.setAmount(prevAmount + transfer.getAmount());
					merged = true;
					break;
				}
			}
			if (!merged) {
				to.addTransfers(transfer);
			}
		}
	}

	private static void mergeNonFungible(final TokenTransferList.Builder from, final TokenTransferList.Builder to) {
		for (int i = 0, n = from.getNftTransfersCount(); i < n; i++) {
			final var fromExchange = from.getNftTransfersBuilder(i);
			var alreadyPresent = false;
			for (int j = 0, m = to.getNftTransfersCount(); j < m; j++) {
				final var toExchange = to.getNftTransfersBuilder(j);
				if (areSameBuilder(fromExchange, toExchange)) {
					alreadyPresent = true;
					break;
				}
			}
			if (!alreadyPresent) {
				to.addNftTransfers(fromExchange);
			}
		}
	}

	static boolean areSameBuilder(final NftTransfer.Builder a, final NftTransfer.Builder b) {
		return a.getSerialNumber() == b.getSerialNumber()
				&& a.getSenderAccountID().equals(b.getSenderAccountID())
				&& a.getReceiverAccountID().equals(b.getReceiverAccountID());
	}

	private TransactionBody.Builder synthCreateOpFromEth(final EthTxData ethTxData) {
		final var op = ContractCreateTransactionBody.newBuilder()
				.setGas(ethTxData.gasLimit())
				.setInitialBalance(ethTxData.value().divide(WEIBARS_TO_TINYBARS).longValueExact())
				.setAutoRenewPeriod(dynamicProperties.typedMinAutoRenewDuration())
				.setInitcode(ByteStringUtils.wrapUnsafely(ethTxData.callData()));
		return TransactionBody.newBuilder().setContractCreateInstance(op);
	}

	private TransactionBody.Builder synthCallOpFromEth(final EthTxData ethTxData) {
		final var targetId = ContractID.newBuilder()
				.setEvmAddress(ByteStringUtils.wrapUnsafely(ethTxData.to()))
				.build();
		final var op = ContractCallTransactionBody.newBuilder()
				.setGas(ethTxData.gasLimit())
				.setAmount(ethTxData.value().divide(WEIBARS_TO_TINYBARS).longValueExact())
				.setContractID(targetId);
		if (ethTxData.hasCallData()) {
			op.setFunctionParameters(ByteStringUtils.wrapUnsafely(ethTxData.callData()));
		}
		return TransactionBody.newBuilder().setContractCall(op);
	}
}
