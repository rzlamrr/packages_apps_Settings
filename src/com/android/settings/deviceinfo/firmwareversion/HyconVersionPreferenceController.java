/*
 * Copyright (C) 2019 The LineageOS Project
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

package com.android.settings.deviceinfo.firmwareversion;

import java.io.IOException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.utils.HyconSpecUtils;
import com.android.settings.Utils;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.slices.Sliceable;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.widget.LayoutPreference;
import com.android.settings.core.PreferenceControllerMixin;

import android.widget.TextView;

public class HyconVersionPreferenceController extends BasePreferenceController {

    private static final String TAG = "hyconVersionDialogCtrl";
    private static final int DELAY_TIMER_MILLIS = 500;
    private static final int ACTIVITY_TRIGGER_COUNT = 3;

    private static final String PROPERTY_HYCON_VERSION = "org.hycon.version";
    private static final String ROM_RELEASETYPE_PROP = "org.pixelexperience.build_type";
    private static final String ROM_CODENAME_PROP = "org.hycon.codename";

    @VisibleForTesting
    TextView mHyconVersionText;
    @VisibleForTesting
    TextView mHyconVersionFlavourText;
    @VisibleForTesting
    TextView mDeviceNameText;
    @VisibleForTesting
    TextView mCpuText;
    @VisibleForTesting
    TextView mScreenResText;
    @VisibleForTesting
    TextView mBatteryText;
    @VisibleForTesting
    TextView mRamText;
    @VisibleForTesting
    TextView mMaintainerText;

    private PreferenceFragmentCompat mHost;
    private LayoutPreference mHyconVersionLayoutPref;
    private Context mContext;

    private final UserManager mUserManager;
    private final long[] mHits = new long[ACTIVITY_TRIGGER_COUNT];

    private RestrictedLockUtils.EnforcedAdmin mFunDisallowedAdmin;
    private boolean mFunDisallowedBySystem;

    public HyconVersionPreferenceController(Context context, String key) {
        super(context, key);
        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        initializeAdminPermissions();
        mContext = context;
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    public void setFragment(PreferenceFragmentCompat fragment) {
        mHost = fragment;
    }

    @Override
    public boolean useDynamicSliceSummary() {
        return true;
    }

    @Override
    public boolean isSliceable() {
        return true;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mHyconVersionLayoutPref = screen.findPreference(getPreferenceKey());
        mHyconVersionText = mHyconVersionLayoutPref.findViewById(R.id.hycon_version);
        mHyconVersionFlavourText = mHyconVersionLayoutPref.findViewById(R.id.hycon_version_flavour);
        mDeviceNameText = mHyconVersionLayoutPref.findViewById(R.id.device_name_text);
        mCpuText = mHyconVersionLayoutPref.findViewById(R.id.device_cpu_text);
        mScreenResText = mHyconVersionLayoutPref.findViewById(R.id.device_screen_res_text);
        mBatteryText = mHyconVersionLayoutPref.findViewById(R.id.device_battery_text);
        mRamText = mHyconVersionLayoutPref.findViewById(R.id.device_ram_text);
        mMaintainerText = mHyconVersionLayoutPref.findViewById(R.id.device_maintainer_text);

        UpdateHyconVersionPreference();
    }

    private void UpdateHyconVersionPreference() {
        // We split the different specs into different voids to make the code more organized.
        updateHyconVersionText();
        updateDeviceNameText();
        updateCpuText();
        updateScreenResText();
        updateBatteryText();
        updateRamText();
        updateMaintainerText();
    }

    private void updateHyconVersionText() {
        String hyconVer = SystemProperties.get(PROPERTY_HYCON_VERSION);
        String hyconType = SystemProperties.get(ROM_RELEASETYPE_PROP);
        String hyconTypeCapitalized = hyconType.substring(0, 1).toUpperCase() + hyconType.substring(1).toLowerCase();
        String[] hyconVerSeparated = hyconVer.split("-");

        if (!hyconVer.isEmpty() && !hyconType.isEmpty()) {
            mHyconVersionText.setText(hyconVerSeparated[0] + " " + hyconTypeCapitalized + " | ");
            mHyconVersionFlavourText.setText(hyconVerSeparated[1]);
        } else {
            mHyconVersionText.setText("");
            mHyconVersionFlavourText.setText(R.string.unknown);
        }
    }

    private void updateDeviceNameText() {
        mDeviceNameText.setText(HyconSpecUtils.getDeviceName());
    }

    private void updateBatteryText() {
        mBatteryText.setText(HyconSpecUtils.getBatteryCapacity(mContext) + " mAh");
    }

    private void updateCpuText() {
        mCpuText.setText(HyconSpecUtils.getProcessorModel());
    }

    private void updateScreenResText() {
        mScreenResText.setText(HyconSpecUtils.getScreenRes(mContext));
    }

    private void updateRamText() {
        mRamText.setText(String.valueOf(HyconSpecUtils.getTotalRAM())+ " GB");
    }
    private void updateMaintainerText() {
        mMaintainerText.setText(String.valueOf(HyconSpecUtils.getMaintainerName()));
    }


    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            return false;
        }
        if (Utils.isMonkeyRunning()) {
            return false;
        }
        arrayCopy();
        mHits[mHits.length - 1] = SystemClock.uptimeMillis();
        if (mHits[0] >= (SystemClock.uptimeMillis() - DELAY_TIMER_MILLIS)) {
            if (mUserManager.hasUserRestriction(UserManager.DISALLOW_FUN)) {
                if (mFunDisallowedAdmin != null && !mFunDisallowedBySystem) {
                    RestrictedLockUtils.sendShowAdminSupportDetailsIntent(mContext,
                            mFunDisallowedAdmin);
                }
                Log.d(TAG, "Sorry, no fun for you!");
                return true;
            }

            final Intent intent = new Intent(Intent.ACTION_MAIN)
                    .setClassName(
                            "android", com.android.internal.app.PlatLogoActivity.class.getName());
            try {
                mContext.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Unable to start activity " + intent.toString());
            }
        }
        return true;
    }

    /**
     * Copies the array onto itself to remove the oldest hit.
     */
    @VisibleForTesting
    void arrayCopy() {
        System.arraycopy(mHits, 1, mHits, 0, mHits.length - 1);
    }

    @VisibleForTesting
    void initializeAdminPermissions() {
        mFunDisallowedAdmin = RestrictedLockUtilsInternal.checkIfRestrictionEnforced(
                mContext, UserManager.DISALLOW_FUN, UserHandle.myUserId());
        mFunDisallowedBySystem = RestrictedLockUtilsInternal.hasBaseUserRestriction(
                mContext, UserManager.DISALLOW_FUN, UserHandle.myUserId());
    }

    @Override
    public void copy() {
        Sliceable.setCopyContent(mContext, getSummary(),
                mContext.getText(R.string.hycon_version));
    }
}
