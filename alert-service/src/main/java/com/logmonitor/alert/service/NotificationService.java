package com.logmonitor.alert.service;

import com.logmonitor.common.entity.AlertRecord;
import com.logmonitor.common.entity.AlertRule;

public interface NotificationService {

    void sendAlert(AlertRule rule, AlertRecord record);
}
