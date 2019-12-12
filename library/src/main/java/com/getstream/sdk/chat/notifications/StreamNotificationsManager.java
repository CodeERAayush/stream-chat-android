package com.getstream.sdk.chat.notifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.aminography.redirectglide.RedirectGlideUrl;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.getstream.sdk.chat.R;
import com.getstream.sdk.chat.StreamChat;
import com.getstream.sdk.chat.enums.EventType;
import com.getstream.sdk.chat.model.Event;
import com.getstream.sdk.chat.model.StreamNotification;
import com.getstream.sdk.chat.notifications.options.NotificationOptions;
import com.getstream.sdk.chat.notifications.options.StreamNotificationOptions;
import com.getstream.sdk.chat.receiver.NotificationMessageReceiver;
import com.getstream.sdk.chat.rest.Message;
import com.getstream.sdk.chat.rest.interfaces.CompletableCallback;
import com.getstream.sdk.chat.rest.interfaces.MessageCallback;
import com.getstream.sdk.chat.rest.response.CompletableResponse;
import com.getstream.sdk.chat.rest.response.MessageResponse;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.internal.LinkedTreeMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static android.content.Context.NOTIFICATION_SERVICE;

/*
 * Created by Anton Bevza on 2019-11-14.
 */
public class StreamNotificationsManager implements NotificationsManager {

    public static final String CHANNEL_ID_KEY = "id";
    public static final String CHANNEL_TYPE_KEY = "type";
    private static final String CHANNEL_KEY = "channel";
    private static final String CHANNEL_NAME_KEY = "name";
    private static final String FIREBASE_MESSAGE_ID_KEY = "message_id";

    private HashMap<String, StreamNotification> notificationsMap = new HashMap<>();

    private static final String TAG = StreamNotificationsManager.class.getSimpleName();

    private NotificationOptions notificationOptions;
    private DeviceRegisteredListener registerListener;

    public StreamNotificationsManager(NotificationOptions notificationOptions, @Nullable DeviceRegisteredListener listener) {
        this.notificationOptions = notificationOptions;
        this.registerListener = listener;
    }

    public StreamNotificationsManager() {
        this(new StreamNotificationOptions(), null);
    }

    @Override
    public void setFirebaseToken(@NotNull String firebaseToken, @NotNull Context context) {
        Log.d(TAG, "setFirebaseToken: " + firebaseToken);

        StreamChat.getInstance(context).addDevice(firebaseToken, new CompletableCallback() {
            @Override
            public void onSuccess(CompletableResponse response) {
                // device is now registered!
                if (registerListener != null) {
                    registerListener.onDeviceRegisteredSuccess();
                }
                Log.i(TAG, "DeviceRegisteredSuccess");
            }

            @Override
            public void onError(String errMsg, int errCode) {
                if (registerListener != null) {
                    registerListener.onDeviceRegisteredError(errMsg, errCode);
                }
                Log.e(TAG, errMsg);
            }
        });
    }

    @Override
    public void onReceiveFirebaseMessage(@NotNull RemoteMessage remoteMessage, @NotNull Context context) {
        Map<String, String> payload = remoteMessage.getData();
        Log.d(TAG, "onReceiveFirebaseMessage: " + remoteMessage.toString() + " data: " + payload);

        handleRemoteMessage(context, remoteMessage);
    }

    @Override
    public void onReceiveWebSocketEvent(@NotNull Event event, @NotNull Context context) {
        Log.d(TAG, "onReceiveWebSocketEvent: " + event);

        handleEvent(context, event);
    }

    /**
     * Handle WebSocket message, check and show notificationsMap
     *
     * @param context - Context
     * @param event   - event from websocket
     */
    public void handleEvent(Context context, Event event) {
        if (event.getType() == EventType.MESSAGE_NEW) {

            String messageId = event.getMessage().getId();

            if (checkSentNotificationWithId(messageId)) {
                StreamNotification notificationModel = new StreamNotification((int) System.currentTimeMillis(), null, event);
                notificationsMap.put(messageId, notificationModel);

                loadMessage(context, messageId);
            } else {
                Log.i(TAG, "Notification with id:" + messageId + " already showed");
            }
        }
    }

    /**
     * Handle Firebase messageText, check and show notificationsMap
     *
     * @param context       - Context
     * @param remoteMessage - msg from Firebase
     */
    public void handleRemoteMessage(Context context, RemoteMessage remoteMessage) {
        String messageId = remoteMessage.getData().get(FIREBASE_MESSAGE_ID_KEY);

        if (checkSentNotificationWithId(messageId)) {

            if (messageId != null && !messageId.isEmpty()) {
                StreamNotification notificationModel = new StreamNotification((int) System.currentTimeMillis(), remoteMessage, null);
                notificationsMap.put(messageId, notificationModel);

                loadMessage(context, messageId);
            } else {
                Log.e(TAG, "RemoteMessage: messageId =" + messageId);
            }
        }
    }

    private void loadMessage(Context context, @NonNull String messageId) {
        StreamChat.getInstance(context).getMessage(messageId, new MessageCallback() {
            @Override
            public void onSuccess(MessageResponse response) {
                onMessageLoaded(context, response.getMessage());
            }

            @Override
            public void onError(String errMsg, int errCode) {
                Log.e(TAG, "Can\'t load message. Error: " + errMsg);
            }
        });
    }

    private void onMessageLoaded(@NotNull Context context, @NotNull Message message) {
        LinkedTreeMap<String, String> channelObj = (LinkedTreeMap<String, String>) message.getExtraData().get(CHANNEL_KEY);

        if (channelObj != null) {
            String messageId = message.getId();
            String channelName = channelObj.get(CHANNEL_NAME_KEY);
            String messageText = message.getText();
            String type = channelObj.get(CHANNEL_TYPE_KEY);
            String id = channelObj.get(CHANNEL_ID_KEY);

            if (checkRequireFields(channelName, messageText)) {
                StreamNotification notificationItem = notificationsMap.get(messageId);

                if (notificationItem != null) {
                    notificationItem.setChannelName(channelObj.get(CHANNEL_NAME_KEY));
                    notificationItem.setMessageText(message.getText());
                    notificationItem.setPendingIntent(preparePendingIntent(context, id, type, notificationItem.getNotificationId()));
                    loadUserImage(context, messageId, message.getUser().getImage());
                }
            } else {
                Log.e(TAG, "One of required fields is null: channelName = " + channelName +
                        ", messageText = " + messageText);
            }
        }
    }

    private boolean checkSentNotificationWithId(@Nullable String messageId) {
        return notificationsMap.get(messageId) == null;
    }

    private boolean checkRequireFields(@Nullable String channelName, @Nullable String message) {
        return channelName != null && !channelName.isEmpty()
                && message != null && !message.isEmpty();
    }

    private Notification prepareNotification(Context context, String messageId, @Nullable Bitmap image) {
        StreamNotification notificationItem = notificationsMap.get(messageId);
        NotificationCompat.Builder notificationBuilder = notificationOptions.getNotificationBuilder(context);

        if (notificationItem != null) {
            PendingIntent contentIntent;
            if (notificationItem.getEvent() != null) {
                contentIntent = notificationOptions.getNotificationIntentProvider()
                        .getIntentForWebSocketEvent(context, notificationItem.getEvent());
            } else {
                contentIntent = notificationOptions.getNotificationIntentProvider()
                        .getIntentForFirebaseMessage(context, notificationItem.getRemoteMessage());
            }

            notificationBuilder.setContentTitle(notificationItem.getChannelName())
                    .setContentText(notificationItem.getMessageText())
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .addAction(getReadAction(context, notificationItem.getPendingIntent()))
                    .addAction(getReplyAction(context, notificationItem.getPendingIntent()))
                    .setShowWhen(true)
                    .setContentIntent(contentIntent);

            if (image != null) {
                notificationBuilder.setLargeIcon(image);
            }
        }

        return notificationBuilder.build();
    }

    private NotificationCompat.Action getReadAction(Context context, PendingIntent pendingIntent) {
        return new NotificationCompat.Action.Builder(android.R.drawable.ic_menu_view,
                context.getString(R.string.stream_default_notification_read), pendingIntent).build();
    }

    private NotificationCompat.Action getReplyAction(Context context, PendingIntent replyPendingIntent) {
        RemoteInput remoteInput = new RemoteInput.Builder(NotificationMessageReceiver.KEY_TEXT_REPLY)
                .setLabel(context.getString(R.string.stream_default_notification_type))
                .build();

        return new NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, context.getString(R.string.stream_default_notification_reply), replyPendingIntent)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build();
    }

    private PendingIntent preparePendingIntent(Context context, String id, String type, int notificationId) {
        Intent notifyIntent = new Intent(context, NotificationMessageReceiver.class);
        notifyIntent.setAction(NotificationMessageReceiver.ACTION_REPLY);
        notifyIntent.putExtra(NotificationMessageReceiver.KEY_NOTIFICATION_ID, notificationId);
        notifyIntent.putExtra(NotificationMessageReceiver.KEY_CHANNEL_ID, id);
        notifyIntent.putExtra(NotificationMessageReceiver.KEY_CHANNEL_TYPE, type);

        return PendingIntent.getBroadcast(
                context,
                0,
                notifyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private void loadUserImage(Context context, String messageId, String photoUrl) {
        StreamNotification notificationItem = notificationsMap.get(messageId);
        if (notificationItem != null) {
            Glide.with(context)
                    .asBitmap()
                    .load(new RedirectGlideUrl(photoUrl, 10))
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            Notification notification = prepareNotification(context,
                                    messageId,
                                    resource
                            );
                            showNotification(notificationItem.getNotificationId(), notification, context);
                            removeNotificationItem(notificationItem.getNotificationId());
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            Notification notification = prepareNotification(context,
                                    messageId,
                                    null
                            );
                            showNotification(notificationItem.getNotificationId(), notification, context);
                            removeNotificationItem(notificationItem.getNotificationId());
                        }
                    });
        }
    }

    private void showNotification(@NotNull Integer notificationId, @NotNull Notification notification, @NotNull Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);

        if (notificationManager != null && !isForeground()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                notificationManager.createNotificationChannel(
                        notificationOptions.getNotificationChannel(context));
            }

            notificationManager.notify(notificationId, notification);
        }
    }

    private synchronized void removeNotificationItem(int notificationId) {
        notificationsMap.remove(notificationId);
    }

    private boolean isForeground() {
        return ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }
}
