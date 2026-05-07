package com.logmonitor.alert.service.impl;

import com.logmonitor.common.entity.AlertRecord;
import com.logmonitor.common.entity.AlertRule;
import com.logmonitor.alert.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StubNotificationService implements NotificationService {

    @Override
    public void sendAlert(AlertRule rule, AlertRecord record) {
        switch (rule.getNotifyType()) {
            case EMAIL:
                log.info("[STUB EMAIL] To: admin@example.com | Subject: Alert - {} | Content: {}",
                        rule.getRuleName(), record.getAlertContent());
                break;
            case DINGTALK:
                log.info("[STUB DINGTALK] Webhook: https://oapi.dingtalk.com/robot/send | Alert: {} | Content: {}",
                        rule.getRuleName(), record.getAlertContent());
                break;
            case ALL:
                log.info("[STUB EMAIL+DINGTALK] Alert: {} | Content: {}",
                        rule.getRuleName(), record.getAlertContent());
                break;
        }
    }
}
