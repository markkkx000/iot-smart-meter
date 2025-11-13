#!/usr/bin/env python3

import sqlite3
import logging
from datetime import datetime
from contextlib import contextmanager

log = logging.getLogger("database")

class Database:
    def __init__(self, db_path):
        self.db_path = db_path
        self.init_database()
 
    @contextmanager
    def get_connection(self):
        """Context manager for database connections"""
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        try:
            yield conn
            conn.commit()
        except Exception as e:
            conn.rollback()
            log.error(f"Database error: {e}")
            raise
        finally:
            conn.close()

    def init_database(self):
        """Create tables if they don't exist"""
        with self.get_connection() as conn:
            conn.execute('''
                CREATE TABLE IF NOT EXISTS schedules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    client_id TEXT NOT NULL,
                    schedule_type TEXT NOT NULL,
                    start_time TEXT,
                    end_time TEXT,
                    duration_seconds INTEGER,
                    days_of_week TEXT,
                    enabled INTEGER DEFAULT 1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            ''')

            conn.execute('''
                CREATE TABLE IF NOT EXISTS thresholds (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    client_id TEXT NOT NULL UNIQUE,
                    limit_kwh REAL NOT NULL,
                    reset_period TEXT NOT NULL,
                    enabled INTEGER DEFAULT 1,
                    last_reset TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            ''')

            conn.execute('''
                CREATE TABLE IF NOT EXISTS energy_readings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    client_id TEXT NOT NULL,
                    energy_kwh REAL NOT NULL,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            ''')

            conn.execute('''
                CREATE TABLE IF NOT EXISTS schedule_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    schedule_id INTEGER NOT NULL,
                    action TEXT NOT NULL,
                    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            ''')

            # Create indexes
            conn.execute('CREATE INDEX IF NOT EXISTS idx_energy_client_time ON energy_readings(client_id, timestamp)')
            conn.execute('CREATE INDEX IF NOT EXISTS idx_schedules_client ON schedules(client_id)')

        log.info("Database initialized")

    def get_all_schedules(self, enabled=None):
        """Get all schedules, optionally filtered by enabled status"""
        with self.get_connection() as conn:
            if enabled is not None:
                cursor = conn.execute('SELECT * FROM schedules WHERE enabled = ?', (enabled,))
            else:
                cursor = conn.execute('SELECT * FROM schedules')
            return [dict(row) for row in cursor.fetchall()]

    def get_schedule(self, schedule_id):
        """Get single schedule by ID"""
        with self.get_connection() as conn:
            cursor = conn.execute('SELECT * FROM schedules WHERE id = ?', (schedule_id,))
            row = cursor.fetchone()
            return dict(row) if row else None

    def add_schedule(self, client_id, schedule_type, **kwargs):
        """Add new schedule"""
        with self.get_connection() as conn:
            cursor = conn.execute('''
                INSERT INTO schedules (client_id, schedule_type, start_time, end_time, 
                                     duration_seconds, days_of_week)
                VALUES (?, ?, ?, ?, ?, ?)
            ''', (
                client_id,
                schedule_type,
                kwargs.get('start_time'),
                kwargs.get('end_time'),
                kwargs.get('duration_seconds'),
                kwargs.get('days_of_week')
            ))
            return cursor.lastrowid

    def delete_schedule(self, schedule_id):
        """Delete schedule"""
        with self.get_connection() as conn:
            conn.execute('DELETE FROM schedules WHERE id = ?', (schedule_id,))

    def update_schedule(self, schedule_id, **kwargs):
        """Update schedule fields"""
        with self.get_connection() as conn:
            # Build UPDATE query dynamically based on provided kwargs
            fields = []
            values = []

            for key, value in kwargs.items():
                if key in ['start_time', 'end_time', 'duration_seconds', 'days_of_week']:
                    fields.append(f"{key} = ?")
                    values.append(value)

            if not fields:
                return  # Nothing to update

            values.append(schedule_id)
            query = f"UPDATE schedules SET {', '.join(fields)} WHERE id = ?"
            conn.execute(query, values)

    def get_all_thresholds(self, enabled=None):
        """Get all thresholds"""
        with self.get_connection() as conn:
            if enabled is not None:
                cursor = conn.execute('SELECT * FROM thresholds WHERE enabled = ?', (enabled,))
            else:
                cursor = conn.execute('SELECT * FROM thresholds')
            return [dict(row) for row in cursor.fetchall()]

    def set_threshold(self, client_id, limit_kwh, reset_period):
        """Set or update threshold for device"""
        with self.get_connection() as conn:
            conn.execute('''
                INSERT INTO thresholds (client_id, limit_kwh, reset_period, enabled)
                VALUES (?, ?, ?, 1)
                ON CONFLICT(client_id) DO UPDATE SET
                    limit_kwh = excluded.limit_kwh,
                    reset_period = excluded.reset_period,
                    enabled = 1
            ''', (client_id, limit_kwh, reset_period))

    def disable_threshold(self, threshold_id):
        """Disable threshold after triggering"""
        with self.get_connection() as conn:
            conn.execute('UPDATE thresholds SET enabled = 0 WHERE id = ?', (threshold_id,))

    def store_energy_reading(self, client_id, energy_kwh):
        """Store energy reading"""
        with self.get_connection() as conn:
            conn.execute('''
                INSERT INTO energy_readings (client_id, energy_kwh)
                VALUES (?, ?)
            ''', (client_id, energy_kwh))

    def get_consumption_since(self, client_id, start_time):
        """Get energy consumption since timestamp, handling meter resets"""
        with self.get_connection() as conn:
            cursor = conn.execute('''
                SELECT energy_kwh, timestamp 
                FROM energy_readings 
                WHERE client_id = ? AND timestamp >= ?
                ORDER BY timestamp ASC
            ''', (client_id, start_time.strftime('%Y-%m-%d %H:%M:%S')))
            
            readings = cursor.fetchall()
            
            if len(readings) < 2:
                return 0.0
            
            # Sum consumption between consecutive readings
            total_consumption = 0.0
            prev_kwh = readings[0]['energy_kwh']
            
            for reading in readings[1:]:
                current_kwh = reading['energy_kwh']
                delta = current_kwh - prev_kwh
                
                # Only count positive deltas (skip meter resets)
                if delta > 0:
                    total_consumption += delta
                
                prev_kwh = current_kwh
            
            return total_consumption

    def log_schedule_execution(self, schedule_id, action):
        """Log schedule execution"""
        with self.get_connection() as conn:
            conn.execute('''
                INSERT INTO schedule_log (schedule_id, action)
                VALUES (?, ?)
            ''', (schedule_id, action))
