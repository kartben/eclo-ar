package net.airvantage.eclo.m2m;

import java.util.Date;

public interface IM2MAlert {
	String getUid();

	Date getDate();

	String getName();

	String getMessage();
}
