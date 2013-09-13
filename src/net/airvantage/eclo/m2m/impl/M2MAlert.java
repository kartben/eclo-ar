package net.airvantage.eclo.m2m.impl;

import java.util.Date;

import net.airvantage.eclo.m2m.IM2MAlert;

public class M2MAlert implements IM2MAlert {

	private String _uid;
	private Date _date;
	private String _name;
	private String _message;

	public M2MAlert(String uid, Date date, String name, String message) {
		_uid = uid;
		_date = date;
		_name = name;
		_message = message;
	}

	@Override
	public String getUid() {
		return _uid;
	}

	@Override
	public Date getDate() {
		return _date;
	}

	@Override
	public String getName() {
		return _name;
	}

	@Override
	public String getMessage() {
		return _message;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((_uid == null) ? 0 : _uid.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		M2MAlert other = (M2MAlert) obj;
		if (_uid == null) {
			if (other._uid != null)
				return false;
		} else if (!_uid.equals(other._uid))
			return false;
		return true;
	}

}
