/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.chat;

import bisq.chat.bisqeasy.channel.BisqEasyChatChannelSelectionService;
import bisq.chat.bisqeasy.channel.priv.BisqEasyPrivateTradeChatChannel;
import bisq.chat.bisqeasy.channel.priv.BisqEasyPrivateTradeChatChannelService;
import bisq.chat.bisqeasy.channel.pub.BisqEasyPublicChatChannel;
import bisq.chat.bisqeasy.channel.pub.BisqEasyPublicChatChannelService;
import bisq.chat.channel.ChatChannel;
import bisq.chat.channel.ChatChannelDomain;
import bisq.chat.channel.ChatChannelSelectionService;
import bisq.chat.channel.ChatChannelService;
import bisq.chat.channel.priv.PrivateChatChannelService;
import bisq.chat.channel.priv.TwoPartyPrivateChatChannel;
import bisq.chat.channel.priv.TwoPartyPrivateChatChannelService;
import bisq.chat.channel.pub.CommonPublicChatChannel;
import bisq.chat.channel.pub.CommonPublicChatChannelService;
import bisq.common.application.Service;
import bisq.common.util.CompletableFutureUtils;
import bisq.network.NetworkService;
import bisq.persistence.PersistenceService;
import bisq.security.pow.ProofOfWorkService;
import bisq.user.identity.UserIdentityService;
import bisq.user.profile.UserProfile;
import bisq.user.profile.UserProfileService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Getter
public class ChatService implements Service {
    private final PersistenceService persistenceService;
    private final ProofOfWorkService proofOfWorkService;
    private final NetworkService networkService;
    private final UserIdentityService userIdentityService;
    private final UserProfileService userProfileService;
    private final BisqEasyPublicChatChannelService bisqEasyPublicChatChannelService;
    private final BisqEasyPrivateTradeChatChannelService bisqEasyPrivateTradeChatChannelService;
    private final Map<ChatChannelDomain, CommonPublicChatChannelService> commonPublicChatChannelServices = new HashMap<>();
    private final Map<ChatChannelDomain, TwoPartyPrivateChatChannelService> twoPartyPrivateChatChannelServices = new HashMap<>();
    private final Map<ChatChannelDomain, ChatChannelSelectionService> chatChannelSelectionServices = new HashMap<>();

    public ChatService(PersistenceService persistenceService,
                       ProofOfWorkService proofOfWorkService,
                       NetworkService networkService,
                       UserIdentityService userIdentityService,
                       UserProfileService userProfileService) {
        this.persistenceService = persistenceService;
        this.proofOfWorkService = proofOfWorkService;
        this.networkService = networkService;
        this.userIdentityService = userIdentityService;
        this.userProfileService = userProfileService;

        //BISQ_EASY
        bisqEasyPublicChatChannelService = new BisqEasyPublicChatChannelService(persistenceService,
                networkService,
                userIdentityService,
                userProfileService);
        bisqEasyPrivateTradeChatChannelService = new BisqEasyPrivateTradeChatChannelService(persistenceService,
                networkService,
                userIdentityService,
                userProfileService,
                proofOfWorkService);
        addToTwoPartyPrivateChatChannelServices(ChatChannelDomain.BISQ_EASY);
        chatChannelSelectionServices.put(ChatChannelDomain.BISQ_EASY, new BisqEasyChatChannelSelectionService(persistenceService,
                bisqEasyPrivateTradeChatChannelService,
                bisqEasyPublicChatChannelService,
                twoPartyPrivateChatChannelServices.get(ChatChannelDomain.BISQ_EASY)));

        // DISCUSSION
        addToCommonPublicChatChannelServices(ChatChannelDomain.DISCUSSION,
                List.of(new CommonPublicChatChannel(ChatChannelDomain.DISCUSSION, "bisq"),
                        new CommonPublicChatChannel(ChatChannelDomain.DISCUSSION, "bitcoin"),
                        new CommonPublicChatChannel(ChatChannelDomain.DISCUSSION, "markets"),
                        new CommonPublicChatChannel(ChatChannelDomain.DISCUSSION, "economy"),
                        new CommonPublicChatChannel(ChatChannelDomain.DISCUSSION, "offTopic")));
        addToTwoPartyPrivateChatChannelServices(ChatChannelDomain.DISCUSSION);
        addToChatChannelSelectionServices(ChatChannelDomain.DISCUSSION);

        // EVENTS
        addToCommonPublicChatChannelServices(ChatChannelDomain.EVENTS,
                List.of(new CommonPublicChatChannel(ChatChannelDomain.EVENTS, "conferences"),
                        new CommonPublicChatChannel(ChatChannelDomain.EVENTS, "meetups"),
                        new CommonPublicChatChannel(ChatChannelDomain.EVENTS, "podcasts"),
                        new CommonPublicChatChannel(ChatChannelDomain.EVENTS, "noKyc"),
                        new CommonPublicChatChannel(ChatChannelDomain.EVENTS, "nodes"),
                        new CommonPublicChatChannel(ChatChannelDomain.EVENTS, "tradeEvents")));
        addToTwoPartyPrivateChatChannelServices(ChatChannelDomain.EVENTS);
        addToChatChannelSelectionServices(ChatChannelDomain.EVENTS);

        // SUPPORT
        addToCommonPublicChatChannelServices(ChatChannelDomain.SUPPORT,
                List.of(new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, "support"),
                        new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, "questions"),
                        new CommonPublicChatChannel(ChatChannelDomain.SUPPORT, "reports")));
        addToTwoPartyPrivateChatChannelServices(ChatChannelDomain.SUPPORT);
        addToChatChannelSelectionServices(ChatChannelDomain.SUPPORT);
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        log.info("initialize");
        List<CompletableFuture<Boolean>> list = new ArrayList<>(List.of(bisqEasyPublicChatChannelService.initialize(),
                bisqEasyPrivateTradeChatChannelService.initialize()));
        list.addAll(commonPublicChatChannelServices.values().stream()
                .map(CommonPublicChatChannelService::initialize)
                .collect(Collectors.toList()));
        list.addAll(twoPartyPrivateChatChannelServices.values().stream()
                .map(PrivateChatChannelService::initialize)
                .collect(Collectors.toList()));
        list.addAll(chatChannelSelectionServices.values().stream()
                .map(ChatChannelSelectionService::initialize)
                .collect(Collectors.toList()));
        return CompletableFutureUtils.allOf(list).thenApply(result -> true);
    }

    @Override
    public CompletableFuture<Boolean> shutdown() {
        log.info("shutdown");
        List<CompletableFuture<Boolean>> list = new ArrayList<>(List.of(bisqEasyPublicChatChannelService.shutdown(),
                bisqEasyPrivateTradeChatChannelService.shutdown()));
        list.addAll(commonPublicChatChannelServices.values().stream()
                .map(CommonPublicChatChannelService::shutdown)
                .collect(Collectors.toList()));
        list.addAll(twoPartyPrivateChatChannelServices.values().stream()
                .map(PrivateChatChannelService::shutdown)
                .collect(Collectors.toList()));
        list.addAll(chatChannelSelectionServices.values().stream()
                .map(ChatChannelSelectionService::shutdown)
                .collect(Collectors.toList()));
        return CompletableFutureUtils.allOf(list).thenApply(result -> true);
    }

    public Optional<ChatChannelService<?, ?, ?>> findChatChannelService(@Nullable ChatChannel<?> chatChannel) {
        if (chatChannel == null) {
            return Optional.empty();
        }
        if (chatChannel instanceof CommonPublicChatChannel) {
            return Optional.of(commonPublicChatChannelServices.get(chatChannel.getChatChannelDomain()));
        } else if (chatChannel instanceof TwoPartyPrivateChatChannel) {
            return Optional.of(twoPartyPrivateChatChannelServices.get(chatChannel.getChatChannelDomain()));
        } else if (chatChannel instanceof BisqEasyPublicChatChannel) {
            return Optional.of(bisqEasyPublicChatChannelService);
        } else if (chatChannel instanceof BisqEasyPrivateTradeChatChannel) {
            return Optional.of(bisqEasyPrivateTradeChatChannelService);
        } else {
            throw new RuntimeException("Unexpected chatChannel instance. chatChannel=" + chatChannel);
        }
    }

    public void reportUserProfile(UserProfile userProfile, String reason) {
        //todo report user to admin and moderators, add reason
        log.info("called reportChatUser {} {}", userProfile, reason);
    }

    public void createAndSelectTwoPartyPrivateChatChannel(ChatChannelDomain chatChannelDomain, UserProfile peer) {
        TwoPartyPrivateChatChannelService chatChannelService = twoPartyPrivateChatChannelServices.get(chatChannelDomain);
        chatChannelService.maybeCreateAndAddChannel(chatChannelDomain, peer)
                .ifPresent(channel -> getChatChannelSelectionService(chatChannelDomain).selectChannel(channel));
    }

    public ChatChannelSelectionService getChatChannelSelectionService(ChatChannelDomain chatChannelDomain) {
        return chatChannelSelectionServices.get(chatChannelDomain);
    }

    public BisqEasyChatChannelSelectionService getBisqEasyChatChannelSelectionService() {
        return (BisqEasyChatChannelSelectionService) getChatChannelSelectionServices().get(ChatChannelDomain.BISQ_EASY);
    }

    private void addToTwoPartyPrivateChatChannelServices(ChatChannelDomain chatChannelDomain) {
        twoPartyPrivateChatChannelServices.put(chatChannelDomain,
                new TwoPartyPrivateChatChannelService(persistenceService,
                        networkService,
                        userIdentityService,
                        userProfileService,
                        proofOfWorkService,
                        chatChannelDomain));
    }

    private void addToCommonPublicChatChannelServices(ChatChannelDomain chatChannelDomain, List<CommonPublicChatChannel> defaultChannels) {
        commonPublicChatChannelServices.put(chatChannelDomain,
                new CommonPublicChatChannelService(persistenceService,
                        networkService,
                        userIdentityService,
                        userProfileService,
                        chatChannelDomain,
                        defaultChannels));
    }

    private void addToChatChannelSelectionServices(ChatChannelDomain chatChannelDomain) {
        chatChannelSelectionServices.put(chatChannelDomain,
                new ChatChannelSelectionService(persistenceService,
                        twoPartyPrivateChatChannelServices.get(chatChannelDomain),
                        commonPublicChatChannelServices.get(chatChannelDomain),
                        chatChannelDomain));
    }
}