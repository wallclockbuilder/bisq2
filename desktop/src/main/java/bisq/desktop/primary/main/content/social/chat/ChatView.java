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

package bisq.desktop.primary.main.content.social.chat;

import bisq.desktop.common.view.View;
import bisq.desktop.components.controls.BisqIconButton;
import bisq.desktop.components.controls.BisqInputTextField;
import bisq.desktop.components.controls.BisqLabel;
import bisq.desktop.components.table.FilterBox;
import bisq.desktop.layout.Layout;
import bisq.desktop.primary.main.content.social.chat.components.UserProfileComboBox;
import bisq.i18n.Res;
import de.jensd.fx.fontawesome.AwesomeIcon;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Callback;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

@Slf4j
public class ChatView extends View<SplitPane, ChatModel, ChatController> {

    private final ListView<ChatMessageListItem> messagesListView;
    private final BisqInputTextField inputField;
    private final BisqLabel selectedChannelLabel;
    private final Button searchButton, notificationsButton, infoButton, closeButton;
    private final ComboBox<UserProfileComboBox.ListItem> userProfileComboBox;
    private final VBox left, sideBar;
    private final FilterBox filterBox;
    private final BisqInputTextField filterBoxRoot;
    private final Pane notificationsSettings;
    private final Pane channelInfo;
    private final ListChangeListener<ChatMessageListItem> messagesListener;
    private final HBox messagesListAndSideBar;
    private Subscription chatUserOverviewRootSubscription;
    private Pane chatUserOverviewRoot;

    public ChatView(ChatModel model, ChatController controller,
                    ComboBox<UserProfileComboBox.ListItem> userProfileComboBox,
                    Pane publicChannelSelection,
                    Pane privateChannelSelection,
                    Pane notificationsSettings,
                    Pane channelInfo) {
        super(new SplitPane(), model, controller);

        this.notificationsSettings = notificationsSettings;
        this.channelInfo = channelInfo;
        this.userProfileComboBox = userProfileComboBox;

        this.root.getStyleClass().add("hide-focus");

        userProfileComboBox.setPadding(new Insets(10, 10, 10, 10));

        left = Layout.vBoxWith(userProfileComboBox, publicChannelSelection, privateChannelSelection);
        left.setMinWidth(150);

        selectedChannelLabel = new BisqLabel();
        selectedChannelLabel.getStyleClass().add("headline-label");
        filterBox = new FilterBox(model.getFilteredChatMessages());
        filterBoxRoot = filterBox.getRoot();
        HBox.setHgrow(filterBoxRoot, Priority.ALWAYS);
        HBox.setMargin(filterBoxRoot, new Insets(0, 0, 0, 10));
        searchButton = BisqIconButton.createIconButton(AwesomeIcon.SEARCH);
        notificationsButton = BisqIconButton.createIconButton(AwesomeIcon.BELL);
        infoButton = BisqIconButton.createIconButton(AwesomeIcon.INFO_SIGN);
        HBox centerToolbar = Layout.hBoxWith(selectedChannelLabel, filterBoxRoot, searchButton, notificationsButton, infoButton);

        messagesListView = new ListView<>();
        messagesListView.setCellFactory(getCellFactory());
        messagesListView.setFocusTraversable(false);
        VBox.setVgrow(messagesListView, Priority.ALWAYS);

        inputField = new BisqInputTextField();
        inputField.setPromptText(Res.get("social.chat.input.prompt"));

        VBox messagesAndInput = Layout.vBoxWith(messagesListView, inputField);
        channelInfo.setMinWidth(200);

        closeButton =  BisqIconButton.createIconButton(AwesomeIcon.REMOVE_SIGN);

        sideBar = Layout.vBoxWith(closeButton, notificationsSettings, channelInfo); 
        sideBar.setAlignment(Pos.TOP_RIGHT);
        messagesListAndSideBar = Layout.hBoxWith(messagesAndInput, sideBar);
        HBox.setHgrow(messagesAndInput, Priority.ALWAYS);
        VBox.setVgrow(messagesListAndSideBar, Priority.ALWAYS);
        VBox center = Layout.vBoxWith(centerToolbar, messagesListAndSideBar);
        center.setPadding(new Insets(10, 10, 10, 10));
        root.setDividerPosition(0, model.getDefaultLeftDividerPosition());
        root.getItems().addAll(left, center);

        messagesListener = c -> messagesListView.scrollTo(model.getFilteredChatMessages().size() - 1);
    }

    @Override
    public void onViewAttached() {
        userProfileComboBox.prefWidthProperty().bind(left.widthProperty());
        selectedChannelLabel.textProperty().bind(model.getSelectedChannelAsString());
        filterBoxRoot.visibleProperty().bind(model.getFilterBoxVisible());
        notificationsSettings.visibleProperty().bind(model.getNotificationsVisible());
        notificationsSettings.managedProperty().bind(model.getNotificationsVisible());
        channelInfo.visibleProperty().bind(model.getChannelInfoVisible());
        channelInfo.managedProperty().bind(model.getChannelInfoVisible());
        sideBar.visibleProperty().bind(model.getSideBarVisible());
        sideBar.managedProperty().bind(model.getSideBarVisible());

        inputField.textProperty().bindBidirectional(model.getTextInput());

        searchButton.setOnAction(e -> controller.onToggleFilterBox());
        notificationsButton.setOnAction(e -> controller.onToggleNotifications());
        infoButton.setOnAction(e -> controller.onToggleChannelInfo());
        closeButton.setOnAction(e -> controller.onCloseSideBar());

        inputField.setOnAction(e -> {
            controller.onSendMessage(inputField.getText());
            inputField.clear();
        });

        model.getFilteredChatMessages().addListener(messagesListener);

        messagesListView.setItems(model.getFilteredChatMessages());

        chatUserOverviewRootSubscription = EasyBind.subscribe(model.getChatUserDetailsRoot(),
                pane -> {
                    if (chatUserOverviewRoot != null) {
                        sideBar.getChildren().remove(chatUserOverviewRoot);
                        chatUserOverviewRoot = null;
                    }

                    if (pane != null) {
                        sideBar.getChildren().add(pane);
                        chatUserOverviewRoot = pane;
                    }
                });
    }

    @Override
    protected void onViewDetached() {
        userProfileComboBox.prefWidthProperty().unbind();
        selectedChannelLabel.textProperty().unbind();
        filterBoxRoot.visibleProperty().unbind();
        notificationsSettings.visibleProperty().unbind();
        notificationsSettings.managedProperty().unbind();
        channelInfo.visibleProperty().unbind();
        channelInfo.managedProperty().unbind();
        sideBar.visibleProperty().unbind();
        sideBar.managedProperty().unbind();

        inputField.textProperty().unbindBidirectional(model.getTextInput());

        searchButton.setOnAction(null);
        notificationsButton.setOnAction(null);
        infoButton.setOnAction(null);
        inputField.setOnAction(null);
        closeButton.setOnAction(null);
        model.getFilteredChatMessages().removeListener(messagesListener);
        chatUserOverviewRootSubscription.unsubscribe();
    }

    private Callback<ListView<ChatMessageListItem>, ListCell<ChatMessageListItem>> getCellFactory() {
        return new Callback<>() {

            @Override
            public ListCell<ChatMessageListItem> call(ListView<ChatMessageListItem> list) {
                return new ListCell<>() {
                    BisqLabel userName = new BisqLabel();
                    BisqLabel time = new BisqLabel();
                    Text message = new Text();
                    ImageView icon = new ImageView();
                    HBox hBox, reactionsBox;
                    VBox vBox, messageBox;
                    Tooltip dateTooltip;
                    Subscription widthSubscription;

                    {
                        userName.setId("chat-user-name");
                        userName.setPadding(new Insets(2, 0, -8, 0));
                        time.getStyleClass().add("message-header");
                        time.setPadding(new Insets(-6, 0, 0, 0));
                        time.setVisible(false);
                        icon.setFitWidth(30);
                        icon.setFitHeight(30);
                        message.setId("chat-message-text");
                        VBox.setMargin(message, new Insets(5, 0, 0, 5));
                        reactionsBox = new HBox();
                        messageBox = Layout.vBoxWith(message, reactionsBox);
                        VBox.setVgrow(messageBox, Priority.ALWAYS);
                        vBox = Layout.vBoxWith(userName, messageBox);
                        HBox.setHgrow(vBox, Priority.ALWAYS);
                        hBox = Layout.hBoxWith(Layout.vBoxWith(icon, time), vBox);
                    }

                    @Override
                    public void updateItem(final ChatMessageListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            message.setText(item.getMessage());
                            time.setText(item.getTime());

                            dateTooltip = new Tooltip(item.getDate());
                            dateTooltip.setShowDelay(Duration.millis(100));
                            Tooltip.install(time, dateTooltip);

                            userName.setText(item.getSenderUserName());
                            userName.setOnMouseClicked(e -> controller.onUserNameClicked(item.getSenderUserName()));

                            icon.setImage(item.getIconImage());
                            icon.setCursor(Cursor.HAND);
                            icon.setOnMouseClicked(e -> controller.onShowChatUserDetails(item.getChatMessage()));

                            hBox.setOnMouseEntered(e -> {
                                time.setVisible(true);
                                messageBox.getStyleClass().add("chat-message-box-active");
                            });
                            hBox.setOnMouseExited(e -> {
                                time.setVisible(false);
                                messageBox.getStyleClass().remove("chat-message-box-active");
                            });

                            widthSubscription = EasyBind.subscribe(messagesListView.widthProperty(),
                                    width -> message.setWrappingWidth(width.doubleValue() - 95));

                            setGraphic(hBox);
                        } else {
                            if (widthSubscription != null) {
                                widthSubscription.unsubscribe();
                            }
                            userName.setOnMouseClicked(null);
                            icon.setOnMouseClicked(null);
                            hBox.setOnMouseEntered(null);
                            hBox.setOnMouseExited(null);
                            icon.setImage(null);
                            setGraphic(null);
                        }
                    }
                };
            }
        };
    }
}
