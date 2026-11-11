package com.medicare.notification;

import com.medicare.dto.inventory.InventoryAlerts;
import com.medicare.enums.NotificationType;
import com.medicare.enums.Role;
import com.medicare.repository.UserRepository;
import com.medicare.service.HospitalClock;
import com.medicare.service.InventoryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Once a day, tells every active administrator about low stock and expiring or expired batches
 * (in-app only). The idempotency key makes a second run on the same day a no-op.
 */
@Component
public class InventoryAlertJob {

    private static final Logger log = LoggerFactory.getLogger(InventoryAlertJob.class);

    private final InventoryService inventoryService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final HospitalClock clock;

    public InventoryAlertJob(InventoryService inventoryService, NotificationService notificationService,
                             UserRepository userRepository, HospitalClock clock) {
        this.inventoryService = inventoryService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${medicare.inventory.alert-cron:0 0 8 * * *}", zone = "${medicare.inventory.alert-zone:Asia/Kolkata}")
    public void scheduled() {
        int sent = runDailyAlerts();
        if (sent > 0) {
            log.info("Inventory alerts sent to {} administrator(s)", sent);
        }
    }

    /** @return number of administrators notified */
    @Transactional
    public int runDailyAlerts() {
        InventoryAlerts a = inventoryService.alerts();
        int low = a.lowStock().size();
        int soon = a.expiringSoon().size();
        int expired = a.expired().size();
        if (low + soon + expired == 0) {
            return 0;
        }
        String message = low + " medicine(s) at or below reorder level, " + soon + " batch(es) expiring within "
                + a.expiryWarningDays() + " days, " + expired + " expired batch(es) still in stock.";
        int sent = 0;
        for (var admin : userRepository.findByRoleAndActiveTrue(Role.ADMIN)) {
            if (notificationService.notify(admin.getId(), NotificationType.OPERATIONAL, "Inventory alerts", message,
                    "/admin/inventory/alerts", "INV_ALERT:" + clock.today() + ":" + admin.getId(), false) != null) {
                sent++;
            }
        }
        return sent;
    }
}
