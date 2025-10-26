#!/usr/bin/env python3

import logging
import signal
import sys
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.date import DateTrigger
from datetime import datetime, timedelta
from mqtt_client import MQTTSchedulerClient
from database import Database

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
log = logging.getLogger("smart-meter-scheduler")

class SmartMeterScheduler:
    def __init__(self):
        self.db = Database('/home/sabado/smart_meter/scheduler.db')
        self.mqtt = MQTTSchedulerClient('localhost', 1883)
        self.scheduler = BackgroundScheduler()

        # Subscribe to energy readings for threshold monitoring
        self.mqtt.on_energy_reading = self.handle_energy_reading

        # Subscribe to configuration topics
        self.mqtt.subscribe_to_configs()

    def start(self):
        """Initialize and start all services"""
        log.info("Starting Smart Meter Scheduler...")

        # Connect MQTT
        self.mqtt.connect()

        # Load and schedule all jobs from database
        self.load_schedules()

        # Start threshold monitoring (every 60 seconds)
        self.scheduler.add_job(
            self.check_thresholds,
            'interval',
            seconds=60,
            id='threshold_monitor'
        )

        # Start scheduler
        self.scheduler.start()
        log.info("Scheduler started successfully")

    def load_schedules(self):
        """Load all enabled schedules from database"""
        schedules = self.db.get_all_schedules(enabled=True)

        for schedule in schedules:
            self.add_schedule_job(schedule)

    def add_schedule_job(self, schedule):
        """Add a schedule to APScheduler"""
        client_id = schedule['client_id']
        schedule_id = schedule['id']

        if schedule['schedule_type'] == 'daily':
            # Daily recurring schedule
            on_time = datetime.strptime(schedule['start_time'], '%H:%M').time()
            off_time = datetime.strptime(schedule['end_time'], '%H:%M').time()

            # Schedule ON
            self.scheduler.add_job(
                self.turn_relay_on,
                trigger=CronTrigger(hour=on_time.hour, minute=on_time.minute),
                args=[client_id, schedule_id],
                id=f'schedule_{schedule_id}_on',
                replace_existing=True
            )

            # Schedule OFF
            self.scheduler.add_job(
                self.turn_relay_off,
                trigger=CronTrigger(hour=off_time.hour, minute=off_time.minute),
                args=[client_id, schedule_id],
                id=f'schedule_{schedule_id}_off',
                replace_existing=True
            )

            log.info(f"Added daily schedule for {client_id}: {on_time} - {off_time}")

        elif schedule['schedule_type'] == 'timer':
            # One-time countdown timer
            duration = schedule['duration_seconds']
            run_time = datetime.now() + timedelta(seconds=duration)

            self.scheduler.add_job(
                self.turn_relay_off,
                trigger=DateTrigger(run_date=run_time),
                args=[client_id, schedule_id],
                id=f'timer_{schedule_id}',
                replace_existing=True
            )

            log.info(f"Added timer for {client_id}: {duration}s (until {run_time})")

    def turn_relay_on(self, client_id, schedule_id):
        """Turn relay ON via MQTT"""
        log.info(f"Schedule {schedule_id}: Turning ON relay for {client_id}")
        self.mqtt.publish_relay_command(client_id, 'RELAY_ON')
        self.db.log_schedule_execution(schedule_id, 'ON')

    def turn_relay_off(self, client_id, schedule_id):
        """Turn relay OFF via MQTT"""
        log.info(f"Schedule {schedule_id}: Turning OFF relay for {client_id}")
        self.mqtt.publish_relay_command(client_id, 'RELAY_OFF')
        self.db.log_schedule_execution(schedule_id, 'OFF')

        # If this was a timer, remove it from database
        schedule = self.db.get_schedule(schedule_id)
        if schedule and schedule['schedule_type'] == 'timer':
            self.db.delete_schedule(schedule_id)

    def handle_energy_reading(self, client_id, energy_kwh):
        """Handle incoming energy reading from ESP32"""
        # Store reading in database
        self.db.store_energy_reading(client_id, energy_kwh)

    def check_thresholds(self):
        """Check all active thresholds"""
        thresholds = self.db.get_all_thresholds(enabled=True)

        for threshold in thresholds:
            client_id = threshold['client_id']
            limit_kwh = threshold['limit_kwh']
            reset_period = threshold['reset_period']

            # Calculate consumption in current period
            period_start = self.calculate_period_start(reset_period)
            consumption = self.db.get_consumption_since(client_id, period_start)

            if consumption >= limit_kwh:
                log.warning(f"Threshold exceeded for {client_id}: {consumption:.2f}/{limit_kwh} kWh")

                # Turn off relay
                self.mqtt.publish_relay_command(client_id, 'RELAY_OFF')

                # Publish alert
                self.mqtt.publish_threshold_alert(client_id, consumption, limit_kwh)

                # Disable threshold to prevent repeated triggers
                self.db.disable_threshold(threshold['id'])

    def calculate_period_start(self, reset_period):
        """Calculate start of reset period"""
        now = datetime.now()

        if reset_period == 'daily':
            return now.replace(hour=0, minute=0, second=0, microsecond=0)
        elif reset_period == 'weekly':
            days_since_monday = now.weekday()
            return (now - timedelta(days=days_since_monday)).replace(
                hour=0, minute=0, second=0, microsecond=0
            )
        elif reset_period == 'monthly':
            return now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)

        return now

    def shutdown(self, signum, frame):
        """Graceful shutdown"""
        log.info("Shutting down scheduler...")
        self.scheduler.shutdown()
        self.mqtt.disconnect()
        sys.exit(0)

if __name__ == '__main__':
    service = SmartMeterScheduler()

    # Handle shutdown signals
    signal.signal(signal.SIGINT, service.shutdown)
    signal.signal(signal.SIGTERM, service.shutdown)

    service.start()

    # Keep running
    signal.pause()
