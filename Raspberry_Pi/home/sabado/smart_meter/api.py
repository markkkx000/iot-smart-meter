#!/usr/bin/env python3

from flask import Flask, jsonify, request
from flask_cors import CORS
import logging
import subprocess
from database import Database
from datetime import datetime, timedelta

app = Flask(__name__)
CORS(app)  # Enable CORS for Android app

# Configure logging
logging.basicConfig(level=logging.INFO)
log = logging.getLogger("scheduler-api")

# Initialize database
db = Database('/home/sabado/smart_meter/scheduler.db')

def restart_scheduler():
    """Restart the scheduler service to reload jobs"""
    try:
        result = subprocess.run(
            ['sudo', 'systemctl', 'restart', 'smart-meter-scheduler.service'],
            capture_output=True,
            text=True,
            timeout=10,
            check=True
        )
        log.info("Scheduler service restarted successfully")
        return True
    except subprocess.CalledProcessError as e:
        log.error(f"Failed to restart scheduler: {e.stderr}")
        return False
    except Exception as e:
        log.error(f"Error during scheduler restart: {e}")
        return False

# ============= SCHEDULES ENDPOINTS =============

@app.route('/api/schedules', methods=['GET'])
def get_all_schedules():
    """Get all schedules for all devices"""
    try:
        schedules = db.get_all_schedules()
        return jsonify({
            'success': True,
            'schedules': schedules
        }), 200
    except Exception as e:
        log.error(f"Error getting schedules: {e}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


@app.route('/api/schedules/<client_id>', methods=['GET'])
def get_device_schedules(client_id):
    """Get all schedules for a specific device"""
    try:
        schedules = db.get_all_schedules()
        device_schedules = [s for s in schedules if s['client_id'] == client_id]
        return jsonify({
            'success': True,
            'client_id': client_id,
            'schedules': device_schedules
        }), 200
    except Exception as e:
        log.error(f"Error getting schedules for {client_id}: {e}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


@app.route('/api/schedules', methods=['POST'])
def create_schedule():
    """
    Create a new schedule

    Request body:
    {
        "client_id": "ESP32-fa641d44",
        "schedule_type": "daily",  // or "timer"
        "start_time": "08:00",     // Required for daily
        "end_time": "20:00",       // Required for daily
        "duration_seconds": 120    // Required for timer
    }
    """
    try:
        data = request.get_json()

        # Validate required fields
        if 'client_id' not in data or 'schedule_type' not in data:
            return jsonify({
                'success': False,
                'error': 'Missing required fields: client_id, schedule_type'
            }), 400

        client_id = data['client_id']
        schedule_type = data['schedule_type']

        # Validate schedule type
        if schedule_type not in ['daily', 'timer']:
            return jsonify({
                'success': False,
                'error': 'schedule_type must be "daily" or "timer"'
            }), 400

        # Type-specific validation
        if schedule_type == 'daily':
            if 'start_time' not in data or 'end_time' not in data:
                return jsonify({
                    'success': False,
                    'error': 'Daily schedules require start_time and end_time'
                }), 400

        if schedule_type == 'timer':
            if 'duration_seconds' not in data:
                return jsonify({
                    'success': False,
                    'error': 'Timer schedules require duration_seconds'
                }), 400

        # Add schedule to database
        schedule_id = db.add_schedule(
            client_id=client_id,
            schedule_type=schedule_type,
            start_time=data.get('start_time'),
            end_time=data.get('end_time'),
            duration_seconds=data.get('duration_seconds'),
            days_of_week=data.get('days_of_week')
        )

        log.info(f"Created schedule {schedule_id} for {client_id}")
        restart_success = restart_scheduler()

        return jsonify({
            'success': True,
            'schedule_id': schedule_id,
            'scheduler_restarted': restart_success,
            'message': 'Schedule created and scheduler restarted successfully!' if restart_success 
                    else 'Schedule created, but scheduler restart failed - restart manually.'
        }), 201

    except Exception as e:
        log.error(f"Error creating schedule: {e}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


@app.route('/api/schedules/<int:schedule_id>', methods=['DELETE'])
def delete_schedule(schedule_id):
    """Delete a schedule by ID"""
    try:
        db.delete_schedule(schedule_id)
        log.info(f"Deleted schedule {schedule_id}")
        restart_success = restart_scheduler()

        return jsonify({
            'success': True,
            'scheduler_restarted': restart_success,
            'message': 'Schedule deleted and scheduler restarted successfully!' if restart_success 
                    else 'Schedule deleted, but scheduler restart failed - restart manually.'
        }), 200

    except Exception as e:
        log.error(f"Error deleting schedule {schedule_id}: {e}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


# ============= THRESHOLDS ENDPOINTS =============

@app.route('/api/thresholds/<client_id>', methods=['GET'])
def get_threshold(client_id):
    """Get threshold for a specific device"""
    try:
        thresholds = db.get_all_thresholds()
        threshold = next((t for t in thresholds if t['client_id'] == client_id), None)

        if threshold:
            return jsonify({
                'success': True,
                'threshold': threshold
            }), 200
        else:
            return jsonify({
                'success': False,
                'error': 'No threshold found for this device'
            }), 404

    except Exception as e:
        log.error(f"Error getting threshold for {client_id}: {e}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


@app.route('/api/thresholds/<client_id>', methods=['PUT'])
def set_threshold(client_id):
    """
    Set or update threshold for a device

    Request body:
    {
        "limit_kwh": 1.5,
        "reset_period": "daily"  // "daily", "weekly", or "monthly"
    }
    """
    try:
        data = request.get_json()

        # Validate required fields
        if 'limit_kwh' not in data or 'reset_period' not in data:
            return jsonify({
                'success': False,
                'error': 'Missing required fields: limit_kwh, reset_period'
            }), 400

        limit_kwh = float(data['limit_kwh'])
        reset_period = data['reset_period']

        # Validate reset_period
        if reset_period not in ['daily', 'weekly', 'monthly']:
            return jsonify({
                'success': False,
                'error': 'reset_period must be "daily", "weekly", or "monthly"'
            }), 400

        # Set threshold in database
        db.set_threshold(client_id, limit_kwh, reset_period)

        log.info(f"Set threshold for {client_id}: {limit_kwh} kWh ({reset_period})")

        return jsonify({
            'success': True,
            'message': 'Threshold set successfully'
        }), 200

    except Exception as e:
        log.error(f"Error setting threshold for {client_id}: {e}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


@app.route('/api/thresholds/<client_id>', methods=['DELETE'])
def delete_threshold(client_id):
    """Delete threshold for a device"""
    try:
        thresholds = db.get_all_thresholds()
        threshold = next((t for t in thresholds if t['client_id'] == client_id), None)

        if threshold:
            db.disable_threshold(threshold['id'])
            # Actually delete instead of just disabling
            with db.get_connection() as conn:
                conn.execute('DELETE FROM thresholds WHERE client_id = ?', (client_id,))

            log.info(f"Deleted threshold for {client_id}")
            return jsonify({
                'success': True,
                'message': 'Threshold deleted successfully'
            }), 200
        else:
            return jsonify({
                'success': False,
                'error': 'No threshold found for this device'
            }), 404

    except Exception as e:
        log.error(f"Error deleting threshold for {client_id}: {e}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


# ============= ENERGY DATA ENDPOINTS =============

@app.route('/api/energy/<client_id>', methods=['GET'])
def get_energy_data(client_id):
    """
    Get energy consumption data for a device
    Query parameters:
    - limit: number of readings (default 100)
    - period: "day", "week", "month" (optional, for aggregated data)
    """
    try:
        limit = request.args.get('limit', 100, type=int)
        period = request.args.get('period', None)

        with db.get_connection() as conn:
            if period:
                # Get aggregated consumption for period
                if period == 'day':
                    period_start = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
                elif period == 'week':
                    days_since_monday = datetime.now().weekday()
                    period_start = (datetime.now() - timedelta(days=days_since_monday)).replace(
                        hour=0, minute=0, second=0, microsecond=0
                    )
                elif period == 'month':
                    period_start = datetime.now().replace(day=1, hour=0, minute=0, second=0, microsecond=0)
                else:
                    return jsonify({
                        'success': False,
                        'error': 'Invalid period. Use "day", "week", or "month"'
                    }), 400

                consumption = db.get_consumption_since(client_id, period_start)

                return jsonify({
                    'success': True,
                    'client_id': client_id,
                    'period': period,
                    'consumption_kwh': round(consumption, 3)
                }), 200
            else:
                # Get recent readings
                cursor = conn.execute('''
                    SELECT energy_kwh, timestamp
                    FROM energy_readings
                    WHERE client_id = ?
                    ORDER BY timestamp DESC
                    LIMIT ?
                ''', (client_id, limit))

                readings = [{'energy_kwh': row['energy_kwh'], 'timestamp': row['timestamp']} 
                           for row in cursor.fetchall()]

                return jsonify({
                    'success': True,
                    'client_id': client_id,
                    'readings': readings
                }), 200

    except Exception as e:
        log.error(f"Error getting energy data for {client_id}: {e}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


# ============= DEVICES ENDPOINT =============

@app.route('/api/devices', methods=['GET'])
def get_devices():
    """Get list of all known devices"""
    try:
        with db.get_connection() as conn:
            # Get unique client IDs from energy_readings
            cursor = conn.execute('''
                SELECT DISTINCT client_id, 
                       MAX(timestamp) as last_seen,
                       MAX(energy_kwh) as current_energy
                FROM energy_readings
                GROUP BY client_id
                ORDER BY last_seen DESC
            ''')

            devices = [{'client_id': row['client_id'], 
                       'last_seen': row['last_seen'],
                       'current_energy_kwh': row['current_energy']} 
                      for row in cursor.fetchall()]

            return jsonify({
                'success': True,
                'devices': devices
            }), 200

    except Exception as e:
        log.error(f"Error getting devices: {e}")
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


# ============= HEALTH CHECK =============

@app.route('/api/health', methods=['GET'])
def health_check():
    """API health check endpoint"""
    return jsonify({
        'success': True,
        'service': 'Smart Meter Scheduler API',
        'version': '1.0',
        'timestamp': datetime.now().isoformat()
    }), 200


if __name__ == '__main__':
    log.info("Starting Scheduler API on port 5001")
    app.run(host='0.0.0.0', port=5001, debug=False)
