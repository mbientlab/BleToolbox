/*******************************************************************************
 * Copyright (c) 2013 Nordic Semiconductor. All Rights Reserved.
 * 
 * The information contained herein is property of Nordic Semiconductor ASA.
 * Terms and conditions of usage are described in detail in NORDIC SEMICONDUCTOR STANDARD SOFTWARE LICENSE AGREEMENT.
 * Licensees are granted free, non-transferable use of the information. NO WARRANTY of ANY KIND is provided. 
 * This heading must NOT be removed from the file.
 ******************************************************************************/

/*
 * NORDIC SEMICONDUTOR EXAMPLE CODE AND LICENSE AGREEMENT
 *
 * You are receiving this document because you have obtained example code ("Software") 
 * from Nordic Semiconductor ASA * ("Licensor"). The Software is protected by copyright 
 * laws and international treaties. All intellectual property rights related to the 
 * Software is the property of the Licensor. This document is a license agreement governing 
 * your rights and obligations regarding usage of the Software. Any variation to the terms 
 * of this Agreement shall only be valid if made in writing by the Licensor.
 * 
 * == Scope of license rights ==
 * 
 * You are hereby granted a limited, non-exclusive, perpetual right to use and modify the 
 * Software in order to create your own software. You are entitled to distribute the 
 * Software in original or modified form as part of your own software.
 *
 * If distributing your software in source code form, a copy of this license document shall 
 * follow with the distribution.
 *   
 * The Licensor can at any time terminate your rights under this license agreement.
 * 
 * == Restrictions on license rights ==
 * 
 * You are not allowed to distribute the Software on its own, without incorporating it into 
 * your own software.  
 * 
 * You are not allowed to remove, alter or destroy any proprietary, 
 * trademark or copyright markings or notices placed upon or contained with the Software.
 *     
 * You shall not use Licensor's name or trademarks without Licensor's prior consent.
 * 
 * == Disclaimer of warranties and limitation of liability ==
 * 
 * YOU EXPRESSLY ACKNOWLEDGE AND AGREE THAT USE OF THE SOFTWARE IS AT YOUR OWN RISK AND THAT THE 
 * SOFTWARE IS PROVIDED *AS IS" WITHOUT ANY WARRANTIES OR CONDITIONS WHATSOEVER. NORDIC SEMICONDUCTOR ASA 
 * DOES NOT WARRANT THAT THE FUNCTIONS OF THE SOFTWARE WILL MEET YOUR REQUIREMENTS OR THAT THE 
 * OPERATION OF THE SOFTWARE WILL BE UNINTERRUPTED OR ERROR FREE. YOU ASSUME RESPONSIBILITY FOR 
 * SELECTING THE SOFTWARE TO ACHIEVE YOUR INTENDED RESULTS, AND FOR THE *USE AND THE RESULTS 
 * OBTAINED FROM THE SOFTWARE.
 * 
 * NORDIC SEMICONDUCTOR ASA DISCLAIM ALL WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED 
 * TO WARRANTIES RELATED TO: NON-INFRINGEMENT, LACK OF VIRUSES, ACCURACY OR COMPLETENESS OF RESPONSES 
 * OR RESULTS, IMPLIED  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * IN NO EVENT SHALL NORDIC SEMICONDUCTOR ASA BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL OR 
 * CONSEQUENTIAL DAMAGES OR FOR ANY DAMAGES WHATSOEVER (INCLUDING BUT NOT LIMITED TO DAMAGES FOR 
 * LOSS OF BUSINESS PROFITS, BUSINESS INTERRUPTION, LOSS OF BUSINESS INFORMATION, PERSONAL INJURY, 
 * LOSS OF PRIVACY OR OTHER PECUNIARY OR OTHER LOSS WHATSOEVER) ARISING OUT OF USE OR INABILITY TO 
 * USE THE SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 * 
 * REGARDLESS OF THE FORM OF ACTION, NORDIC SEMICONDUCTOR ASA AGGREGATE LIABILITY ARISING OUT OF 
 * OR RELATED TO THIS AGREEMENT SHALL NOT EXCEED THE TOTAL AMOUNT PAYABLE BY YOU UNDER THIS AGREEMENT. 
 * THE FOREGOING LIMITATIONS, EXCLUSIONS AND DISCLAIMERS SHALL APPLY TO THE MAXIMUM EXTENT ALLOWED BY 
 * APPLICABLE LAW.
 * 
 * == Dispute resolution and legal venue ==
 * 
 * Any and all disputes arising out of the rights and obligations in this license agreement shall be 
 * submitted to ordinary court proceedings. You accept the Oslo City Court as legal venue under this agreement.
 * 
 * This license agreement shall be governed by Norwegian law.
 * 
 * == Contact information ==
 * 
 * All requests regarding the Software or the API shall be directed to: 
 * Nordic Semiconductor ASA, P.O. Box 436, Skøyen, 0213 Oslo, Norway.
 * 
 * http://www.nordicsemi.com/eng/About-us/Contact-us
 */
package no.nordicsemi.android.nrftoolbox;

import com.mbientlab.bletoolbox.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class AppHelpFragment extends DialogFragment {
	private static final String ARG_TEXT = "ARG_TEXT";
	private static final String ARG_VERSION = "ARG_VERSION";

	public static AppHelpFragment getInstance(final int aboutResId, final boolean appendVersion) {
		final AppHelpFragment fragment = new AppHelpFragment();

		final Bundle args = new Bundle();
		args.putInt(ARG_TEXT, aboutResId);
		args.putBoolean(ARG_VERSION, appendVersion);
		fragment.setArguments(args);

		return fragment;
	}

	public static AppHelpFragment getInstance(final int aboutResId) {
		final AppHelpFragment fragment = new AppHelpFragment();

		final Bundle args = new Bundle();
		args.putInt(ARG_TEXT, aboutResId);
		args.putBoolean(ARG_VERSION, false);
		fragment.setArguments(args);

		return fragment;
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState) {
		final Bundle args = getArguments();
		final StringBuilder text = new StringBuilder(getString(args.getInt(ARG_TEXT)));

		final boolean appendVersion = args.getBoolean(ARG_VERSION);
		if (appendVersion) {
			try {
				final String version = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
				text.append(getString(R.string.about_version, version));
			} catch (final NameNotFoundException e) {
				// do nothing
			}
		}
		return new AlertDialog.Builder(getActivity()).setTitle(R.string.about_title).setMessage(text).setPositiveButton(android.R.string.ok, null).create();
	}
}
