/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import ClayLoadingIndicator from '@clayui/loading-indicator';
import {fetch} from 'frontend-js-web';
import React, {useEffect, useState} from 'react';

import StatusLabel, {
	getSignatureStatusDisplayType,
	getSignatureStatusLabel,
} from './SignatureStatusLabel';

import './signature_details.scss';

const SEPARATOR = ` ${String.fromCharCode(183)} `;

function formatDate(time) {
	if (!time) {
		return '';
	}

	return new Date(time).toLocaleString();
}

function getActivities(detail) {
	const activities = [];

	detail.recipients.forEach((recipient) => {
		if (recipient.sentDate) {
			activities.push({
				detail: recipient.name,
				time: recipient.sentDate,
				title: Liferay.Language.get('sent'),
				type: 'info',
			});
		}

		if (recipient.statusDate) {
			const status = getRecipientStatus(recipient);

			activities.push({
				detail: recipient.name,
				time: recipient.statusDate,
				title: getSignatureStatusLabel(status),
				type: getSignatureStatusDisplayType(status),
			});
		}
	});

	if (
		(detail.requestStatus === 'voided' ||
			detail.requestStatus === 'expired') &&
		detail.statusDate
	) {
		activities.push({
			detail: '',
			time: detail.statusDate,
			title: getSignatureStatusLabel(detail.requestStatus),
			type: getSignatureStatusDisplayType(detail.requestStatus),
		});
	}

	activities.sort((a, b) => (a.time || 0) - (b.time || 0));

	return activities;
}

function getRecipientDate(recipient) {
	return recipient.statusDate || recipient.sentDate;
}

function getRecipientStatus(recipient) {
	if (recipient.requestRecipientStatus === 'completed') {
		return 'signed';
	}

	return recipient.requestRecipientStatus;
}

function SignatureDetailsContent({detail}) {
	const ordered =
		new Set(detail.recipients.map(({signingOrder}) => signingOrder)).size >
		1;

	return (
		<div className="signature-details">
			<div className="bg-light border mb-4 p-3 rounded">
				<div className="small text-secondary text-uppercase">
					{Liferay.Language.get('status')}
				</div>

				<div className="mb-3">
					<StatusLabel status={detail.requestStatus} />
				</div>

				<div className="small text-secondary text-uppercase">
					{Liferay.Language.get('requester')}
				</div>

				<div className="font-weight-semi-bold">
					{detail.requesterName}
				</div>

				<div className="mb-3 text-secondary">
					{detail.requesterEmailAddress}
				</div>

				<div className="small text-secondary text-uppercase">
					{Liferay.Language.get('envelope-id')}
				</div>

				<div className={detail.expirationDate ? 'mb-3' : ''}>
					{detail.providerRequestId}
				</div>

				{detail.expirationDate ? (
					<>
						<div className="small text-secondary text-uppercase">
							{Liferay.Language.get('expiration-date')}
						</div>

						<div>{formatDate(detail.expirationDate)}</div>
					</>
				) : null}
			</div>

			<h5>{Liferay.Language.get('recipients')}</h5>

			<table className="mb-4 table table-list">
				<thead>
					<tr>
						{ordered && <th>{Liferay.Language.get('order')}</th>}

						<th>{Liferay.Language.get('name')}</th>

						<th>{Liferay.Language.get('email')}</th>

						<th>{Liferay.Language.get('status')}</th>

						<th>{Liferay.Language.get('date')}</th>
					</tr>
				</thead>

				<tbody>
					{detail.recipients.map((recipient, index) => (
						<tr key={index}>
							{ordered && <td>{recipient.signingOrder}</td>}

							<td>{recipient.name}</td>

							<td>{recipient.emailAddress}</td>

							<td>
								<StatusLabel
									status={getRecipientStatus(recipient)}
								/>
							</td>

							<td>{formatDate(getRecipientDate(recipient))}</td>
						</tr>
					))}
				</tbody>
			</table>

			<h5>{Liferay.Language.get('activity')}</h5>

			<ul className="timeline">
				{getActivities(detail).map((activity, index) => (
					<li className="timeline-item" key={index}>
						<div className="panel panel-secondary">
							<div className="timeline-increment">
								<span
									className={`timeline-icon bg-${activity.type}`}
								/>
							</div>

							<div className="panel-body">
								<div className="font-weight-semi-bold">
									{activity.title}
								</div>

								<div className="text-secondary">
									{[
										activity.detail,
										formatDate(activity.time),
									]
										.filter(Boolean)
										.join(SEPARATOR)}
								</div>
							</div>
						</div>
					</li>
				))}
			</ul>
		</div>
	);
}

export default function SignatureDetails({url}) {
	const [detail, setDetail] = useState(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		let mounted = true;

		fetch(url)
			.then((response) => response.json())
			.then((data) => {
				if (mounted) {
					setDetail(data);
					setLoading(false);
				}
			})
			.catch(() => {
				if (mounted) {
					setLoading(false);
				}
			});

		return () => {
			mounted = false;
		};
	}, [url]);

	if (loading) {
		return <ClayLoadingIndicator />;
	}

	if (detail && detail.providerRequestId) {
		return <SignatureDetailsContent detail={detail} />;
	}

	return (
		<div className="text-secondary">
			{Liferay.Language.get(
				'no-signature-request-was-found-for-this-document'
			)}
		</div>
	);
}
