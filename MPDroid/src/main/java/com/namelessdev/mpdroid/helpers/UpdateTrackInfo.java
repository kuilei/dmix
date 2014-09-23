/*
 * Copyright (C) 2010-2014 The MPDroid Project
 *
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
 */

package com.namelessdev.mpdroid.helpers;

import com.namelessdev.mpdroid.MPDApplication;
import com.namelessdev.mpdroid.R;

import org.a0z.mpd.AlbumInfo;
import org.a0z.mpd.MPDStatus;
import org.a0z.mpd.item.Music;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * This sets up an AsyncTask to gather and parse all the information to update the
 * track information outside of the UI thread, then sends a callback to the resource
 * listeners.
 */
public class UpdateTrackInfo {

    private static final boolean DEBUG = false;

    private static final String TAG = "UpdateTrackInfo";

    private final MPDApplication mApp = MPDApplication.getInstance();

    private final SharedPreferences mSharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(mApp);

    private boolean mForceCoverUpdate = false;

    private FullTrackInfoUpdate mFullTrackInfoListener = null;

    private String mLastAlbum = null;

    private String mLastArtist = null;

    private TrackInfoUpdate mTrackInfoListener = null;

    public UpdateTrackInfo() {
        super();
    }

    public final void addCallback(final FullTrackInfoUpdate listener) {
        mFullTrackInfoListener = listener;
    }

    public final void refresh(final MPDStatus mpdStatus, final boolean forceCoverUpdate) {
        mForceCoverUpdate = forceCoverUpdate;
        new UpdateTrackInfoAsync().execute(mpdStatus);
    }

    public final void refresh(final MPDStatus mpdStatus) {
        refresh(mpdStatus, false);
    }

    public final void removeCallback(final FullTrackInfoUpdate ignored) {
        mFullTrackInfoListener = null;
    }

    public final void removeCallback(final TrackInfoUpdate ignored) {
        mTrackInfoListener = null;
    }

    public final void addCallback(final TrackInfoUpdate listener) {
        mTrackInfoListener = listener;
    }

    public interface FullTrackInfoUpdate {

        /**
         * This is called when cover art needs to be updated due to server information change.
         *
         * @param albumInfo The current albumInfo
         */
        void onCoverUpdate(AlbumInfo albumInfo);

        /**
         * Called when a track information change has been detected.
         *
         * @param updatedSong The currentSong item object.
         * @param album       The album change.
         * @param artist      The artist change.
         * @param date        The date change.
         * @param title       The title change.
         */
        void onTrackInfoUpdate(Music updatedSong, CharSequence album, CharSequence artist,
                CharSequence date, CharSequence title);
    }

    public interface TrackInfoUpdate {

        /**
         * This is called when cover art needs to be updated due to server information change.
         *
         * @param albumInfo The current albumInfo
         */
        void onCoverUpdate(AlbumInfo albumInfo);

        /**
         * Called when a track information change has been detected.
         *
         * @param artist The artist change.
         * @param title  The title change.
         */
        void onTrackInfoUpdate(CharSequence artist, CharSequence title);
    }

    private class UpdateTrackInfoAsync extends AsyncTask<MPDStatus, Void, Void> {

        private String mAlbum = null;

        private AlbumInfo mAlbumInfo = null;

        private String mArtist = null;

        private Music mCurrentSong = null;

        private String mDate = null;

        private boolean mHasCoverChanged = false;

        private String mTitle = null;

        /**
         * Gather and parse all song track information necessary after change.
         *
         * @param params A {@code MPDStatus} object array.
         * @return A null {@code Void} object, ignore it.
         */
        @Override
        protected final Void doInBackground(final MPDStatus... params) {
            final int songPos = params[0].getSongPos();
            mCurrentSong = mApp.oMPDAsyncHelper.oMPD.getPlaylist().getByIndex(songPos);

            if (mCurrentSong != null) {
                if (mCurrentSong.isStream()) {
                    if (mCurrentSong.hasTitle()) {
                        mAlbum = mCurrentSong.getName();
                        mTitle = mCurrentSong.getTitle();
                    } else {
                        mTitle = mCurrentSong.getName();
                    }

                    mArtist = mCurrentSong.getArtist();
                    mAlbumInfo = new AlbumInfo(mArtist, mAlbum);
                } else {
                    mAlbum = mCurrentSong.getAlbum();

                    mDate = Long.toString(mCurrentSong.getDate());
                    if (mDate.isEmpty() || mDate.charAt(0) == '-') {
                        mDate = "";
                    } else {
                        mDate = " - " + mDate;
                    }

                    mTitle = mCurrentSong.getTitle();
                    setArtist();
                    mAlbumInfo = mCurrentSong.getAlbumInfo();
                }
                mHasCoverChanged = hasCoverChanged();

                if (DEBUG) {
                    Log.i(TAG,
                            "mAlbum: " + mAlbum +
                                    " mArtist: " + mArtist +
                                    " mDate: " + mDate +
                                    " mAlbumInfo: " + mAlbumInfo +
                                    " mHasTrackChanged: " + mHasCoverChanged +
                                    " mCurrentSong: " + mCurrentSong +
                                    " mForceCoverUpdate: " + mForceCoverUpdate
                    );
                }
            }

            mLastAlbum = mAlbum;
            mLastArtist = mArtist;

            return (Void) null;
        }

        /**
         * Send out the messages to listeners.
         */
        @Override
        protected final void onPostExecute(final Void result) {
            super.onPostExecute(result);

            final boolean sendCoverUpdate = mHasCoverChanged || mCurrentSong == null
                    || mForceCoverUpdate;

            if (mCurrentSong == null) {
                mTitle = mApp.getResources().getString(R.string.noSongInfo);
            }

            if (mFullTrackInfoListener != null) {
                mFullTrackInfoListener
                        .onTrackInfoUpdate(mCurrentSong, mAlbum, mArtist, mDate, mTitle);

                if (sendCoverUpdate) {
                    if (DEBUG) {
                        Log.e(TAG, "Sending cover update to full track info listener.");
                    }
                    mFullTrackInfoListener.onCoverUpdate(mAlbumInfo);
                }
            }

            if (mTrackInfoListener != null) {
                mTrackInfoListener.onTrackInfoUpdate(mAlbum, mTitle);

                if (sendCoverUpdate) {
                    if (DEBUG) {
                        Log.d(TAG, "Sending cover update to track info listener.");
                    }
                    mTrackInfoListener.onCoverUpdate(mAlbumInfo);
                }
            }
        }

        private boolean hasCoverChanged() {
            final boolean invalid = mArtist == null || mAlbum == null;
            return invalid || !mArtist.equals(mLastArtist) || !mAlbum.equals(mLastAlbum);
        }

        /**
         * If not a stream, this sets up the mArtist based on mArtist and album mArtist
         * information.
         */
        private void setArtist() {
            final boolean showAlbumArtist = mSharedPreferences.getBoolean("showAlbumArtist", true);
            final String albumArtist = mCurrentSong.getAlbumArtist();

            mArtist = mCurrentSong.getArtist();
            if (mArtist.isEmpty()) {
                mArtist = albumArtist;
            } else if (showAlbumArtist && albumArtist != null &&
                    !mArtist.toLowerCase().contains(albumArtist.toLowerCase())) {
                mArtist = albumArtist + " / " + mArtist;
            }
        }
    }
}
