package com.twilio.video;

import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import com.twilio.video.base.BaseClientTest;
import com.twilio.video.helper.CallbackHelper;
import com.twilio.video.test.BuildConfig;
import com.twilio.video.ui.MediaTestActivity;
import com.twilio.video.util.CredentialsUtils;
import com.twilio.video.util.Constants;
import com.twilio.video.util.FakeVideoCapturer;
import com.twilio.video.util.PermissionUtils;
import com.twilio.video.util.RandUtils;
import com.twilio.video.util.Topology;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class RoomTest extends BaseClientTest {
    @Rule
    public ActivityTestRule<MediaTestActivity> activityRule =
            new ActivityTestRule<>(MediaTestActivity.class);
    private MediaTestActivity mediaTestActivity;
    private String identity;
    private String token;
    private String roomName;
    private LocalMedia localMedia;

    @Before
    public void setup() throws InterruptedException {
        super.setup();
        mediaTestActivity = activityRule.getActivity();
        PermissionUtils.allowPermissions(mediaTestActivity);
        identity = Constants.PARTICIPANT_ALICE;
        token = CredentialsUtils.getAccessToken(identity);
        roomName = RandUtils.generateRandomString(20);
        localMedia = LocalMedia.create(mediaTestActivity);
    }

    @After
    public void teardown() {
        if (localMedia != null) {
            localMedia.release();
        }
    }

    @Test
    public void shouldReturnLocalParticipantOnConnected() throws InterruptedException {
        CallbackHelper.FakeRoomListener roomListener = new CallbackHelper.FakeRoomListener();
        roomListener.onConnectedLatch = new CountDownLatch(1);

        ConnectOptions connectOptions = new ConnectOptions.Builder(token)
                .roomName(roomName)
                .localMedia(localMedia)
                .build();
        Room room = VideoClient.connect(mediaTestActivity, connectOptions, roomListener);
        assertNull(room.getLocalParticipant());
        assertTrue(roomListener.onConnectedLatch.await(20, TimeUnit.SECONDS));

        LocalParticipant localParticipant = room.getLocalParticipant();
        assertNotNull(localParticipant);
        assertEquals(identity, localParticipant.getIdentity());
        assertEquals(localMedia, localParticipant.getLocalMedia());
        assertNotNull(localParticipant.getSid());
        assertTrue(!localParticipant.getSid().isEmpty());
        room.disconnect();
    }

    @Test
    public void shouldAllowAddingAndRemovingTracksWhileConnected() throws InterruptedException {
        CallbackHelper.FakeRoomListener roomListener = new CallbackHelper.FakeRoomListener();
        FakeVideoCapturer fakeVideoCapturer = new FakeVideoCapturer();
        roomListener.onConnectedLatch = new CountDownLatch(1);

        ConnectOptions connectOptions = new ConnectOptions.Builder(token)
                .roomName(roomName)
                .localMedia(localMedia)
                .build();
        Room room = VideoClient.connect(mediaTestActivity, connectOptions, roomListener);
        assertTrue(roomListener.onConnectedLatch.await(20, TimeUnit.SECONDS));
        assertNotNull(room.getLocalParticipant().getLocalMedia());

        // Now we add our tracks
        LocalAudioTrack localAudioTrack = localMedia.addAudioTrack(true);
        LocalVideoTrack localVideoTrack = localMedia.addVideoTrack(true, fakeVideoCapturer);

        // Now remove them
        assertTrue(localMedia.removeAudioTrack(localAudioTrack));
        assertTrue(localMedia.removeVideoTrack(localVideoTrack));
        room.disconnect();
    }

    @Test
    public void shouldReconnect() throws InterruptedException {
        ConnectOptions connectOptions = new ConnectOptions.Builder(token)
            .roomName(roomName)
            .build();
        for (int i=0; i < 5; i++) {
            CallbackHelper.FakeRoomListener roomListener = new CallbackHelper.FakeRoomListener();
            roomListener.onConnectedLatch = new CountDownLatch(1);
            roomListener.onDisconnectedLatch = new CountDownLatch(1);

            Room room = VideoClient.connect(mediaTestActivity, connectOptions, roomListener);
            assertTrue(roomListener.onConnectedLatch.await(20, TimeUnit.SECONDS));
            assertEquals(RoomState.CONNECTED, room.getState());

            room.disconnect();
            assertTrue(roomListener.onDisconnectedLatch.await(20, TimeUnit.SECONDS));
            assertEquals(RoomState.DISCONNECTED, room.getState());
        }
    }

    @Test
    public void shouldFailToConnectWithInvalidToken() throws InterruptedException {
        String invalidToken = "invalid token";
        ConnectOptions connectOptions = new ConnectOptions.Builder(invalidToken).build();
        final CountDownLatch connectFailure = new CountDownLatch(1);
        VideoClient.connect(mediaTestActivity, connectOptions, new Room.Listener() {
            @Override
            public void onConnected(Room room) {
                fail();
            }

            @Override
            public void onConnectFailure(Room room, TwilioException twilioException) {
                assertEquals(Room.ERROR_INVALID_ACCESS_TOKEN, twilioException.getCode());
                connectFailure.countDown();
            }

            @Override
            public void onDisconnected(Room room, TwilioException twilioException) {
                fail();
            }

            @Override
            public void onParticipantConnected(Room room, Participant participant) {
                fail();
            }

            @Override
            public void onParticipantDisconnected(Room room, Participant participant) {
                fail();
            }

            @Override
            public void onRecordingStarted(Room room) {
                fail();
            }

            @Override
            public void onRecordingStopped(Room room) {
                fail();
            }
        });
        assertTrue(connectFailure.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void shouldReturnValidRecordingState() throws InterruptedException {
        CallbackHelper.FakeRoomListener roomListener = new CallbackHelper.FakeRoomListener();
        roomListener.onConnectedLatch = new CountDownLatch(1);

        ConnectOptions connectOptions = new ConnectOptions.Builder(token)
                .roomName(roomName)
                .localMedia(localMedia)
                .build();
        Room room = VideoClient.connect(mediaTestActivity, connectOptions, roomListener);
        assertNull(room.getLocalParticipant());
        assertTrue(roomListener.onConnectedLatch.await(20, TimeUnit.SECONDS));

        Topology topology = Topology.fromString(BuildConfig.TOPOLOGY);
        if(topology == Topology.P2P || topology == Topology.SFU) {
           Assert.assertFalse(room.isRecording());
        } else {
            /*
             * Making an assumption that other topologies, will have recording enabled by default.
             * This assumption is subject to change and we will have to update this test
             * accordingly.
             */
            Assert.assertTrue(room.isRecording());
        }

        room.disconnect();

        Assert.assertFalse(room.isRecording());
    }

}
