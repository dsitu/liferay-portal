/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.model;

import com.liferay.digital.signature.constants.DigitalSignatureConstants;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.Validator;

import java.io.Serializable;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Brian I. Kim
 * @author Danny Situ
 */
public class DSRequest implements Serializable {

	public DSRequest(
		long companyId, Date createDate, long dsRequestId,
		List<DSRequestRecipient> dsRequestRecipients, List<Long> fileEntryIds,
		String requesterEmailAddress, String requesterName,
		long requesterUserId, Map<String, Serializable> values) {

		_companyId = companyId;
		_createDate = createDate;
		_dsRequestId = dsRequestId;
		_dsRequestRecipients = dsRequestRecipients;
		_fileEntryIds = fileEntryIds;
		_requesterEmailAddress = requesterEmailAddress;
		_requesterName = requesterName;
		_requesterUserId = requesterUserId;

		_emailBody = GetterUtil.getString(values.get("emailBody"));
		_emailSubject = GetterUtil.getString(values.get("emailSubject"));
		_expirationDate = _toDate(values.get("requestExpirationDate"));
		_providerKey = GetterUtil.getString(values.get("providerKey"));
		_providerRequestId = GetterUtil.getString(
			values.get("providerRequestId"));
		_siteId = GetterUtil.getLong(values.get("siteId"));
		_status = GetterUtil.getString(values.get("requestStatus"));
		_statusDate = _toDate(values.get("requestStatusDate"));
	}

	public long getCompanyId() {
		return _companyId;
	}

	public Date getCreateDate() {
		if (_createDate == null) {
			return null;
		}

		return new Date(_createDate.getTime());
	}

	public long getDSRequestId() {
		return _dsRequestId;
	}

	public List<DSRequestRecipient> getDSRequestRecipients() {
		if (_dsRequestRecipients == null) {
			return Collections.emptyList();
		}

		return _dsRequestRecipients;
	}

	public String getEmailBody() {
		return _emailBody;
	}

	public String getEmailSubject() {
		return _emailSubject;
	}

	public Date getExpirationDate() {
		if (_expirationDate == null) {
			return null;
		}

		return new Date(_expirationDate.getTime());
	}

	public List<Long> getFileEntryIds() {
		if (_fileEntryIds == null) {
			return Collections.emptyList();
		}

		return _fileEntryIds;
	}

	public String getProviderKey() {
		return _providerKey;
	}

	public String getProviderRequestId() {
		return _providerRequestId;
	}

	public String getRequesterEmailAddress() {
		return _requesterEmailAddress;
	}

	public String getRequesterName() {
		return _requesterName;
	}

	public long getRequesterUserId() {
		return _requesterUserId;
	}

	public long getSiteId() {
		return _siteId;
	}

	public String getStatus() {
		if ((_expirationDate != null) &&
			!ArrayUtil.contains(
				DigitalSignatureConstants.REQUEST_STATUSES_TERMINAL, _status) &&
			_expirationDate.before(new Date())) {

			return "expired";
		}

		return _status;
	}

	public Date getStatusDate() {
		if (_statusDate == null) {
			return null;
		}

		return new Date(_statusDate.getTime());
	}

	public boolean isRequestable() {
		if (isTerminal() && !Objects.equals(getStatus(), "completed")) {
			return true;
		}

		return false;
	}

	public boolean isSignatureRequired(long userId) {
		if ((userId <= 0) || isTerminal()) {
			return false;
		}

		for (DSRequestRecipient dsRequestRecipient : getDSRequestRecipients()) {
			if ((dsRequestRecipient.getUserId() != userId) ||
				!ArrayUtil.contains(
					DigitalSignatureConstants.
						REQUEST_RECIPIENT_STATUSES_PENDING,
					dsRequestRecipient.getStatus())) {

				continue;
			}

			return true;
		}

		return false;
	}

	public boolean isTerminal() {
		String status = getStatus();

		if (Validator.isNull(status)) {
			return false;
		}

		return ArrayUtil.contains(
			DigitalSignatureConstants.REQUEST_STATUSES_TERMINAL, status);
	}

	private Date _toDate(Serializable value) {
		if (value instanceof Date) {
			return (Date)value;
		}

		return null;
	}

	private final long _companyId;
	private final Date _createDate;
	private final long _dsRequestId;
	private final List<DSRequestRecipient> _dsRequestRecipients;
	private final String _emailBody;
	private final String _emailSubject;
	private final Date _expirationDate;
	private final List<Long> _fileEntryIds;
	private final String _providerKey;
	private final String _providerRequestId;
	private final String _requesterEmailAddress;
	private final String _requesterName;
	private final long _requesterUserId;
	private final long _siteId;
	private final String _status;
	private final Date _statusDate;

}