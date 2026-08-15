# smart_monitor.py — Python версия

import subprocess
import json
import re
import sys
import os
import csv
from datetime import datetime
from colorama import init, Fore, Style

init(autoreset=True)

class SMARTMonitor:
    def __init__(self):
        self.devices = []
        self.smart_data = {}

    def get_devices(self):
        """Получает список всех дисков с помощью smartctl --scan."""
        try:
            output = subprocess.check_output(['smartctl', '--scan'], 
                                              stderr=subprocess.DEVNULL,
                                              universal_newlines=True)
            for line in output.strip().split('\n'):
                if line:
                    parts = line.split()
                    if parts:
                        self.devices.append(parts[0])
        except FileNotFoundError:
            print(Fore.RED + "❌ smartctl не найден. Установите smartmontools.")
            sys.exit(1)
        return self.devices

    def get_smart_data(self, device):
        """Получает SMART-данные для устройства в формате JSON."""
        try:
            output = subprocess.check_output(
                ['smartctl', '-j', '-a', device],
                stderr=subprocess.DEVNULL,
                universal_newlines=True
            )
            return json.loads(output)
        except subprocess.CalledProcessError:
            return None
        except json.JSONDecodeError:
            return None

    def parse_attributes(self, data):
        """Извлекает ключевые SMART-атрибуты."""
        if not data:
            return None

        result = {
            'model': data.get('model_name', 'Unknown'),
            'serial': data.get('serial_number', 'Unknown'),
            'firmware': data.get('firmware_version', 'Unknown'),
            'smart_supported': data.get('smart_supported', False),
            'smart_enabled': data.get('smart_enabled', False),
            'smart_status': data.get('smart_status', {}).get('passed', False),
            'temperature': None,
            'power_on_hours': None,
            'power_cycles': None,
            'read_errors': None,
            'write_errors': None,
            'reallocated_sectors': None,
            'pending_sectors': None,
            'uncorrectable_sectors': None,
        }

        # Извлекаем атрибуты из ata_smart_attributes или nvme_smart_health_information_log
        if 'ata_smart_attributes' in data:
            attrs = data['ata_smart_attributes'].get('table', [])
            for attr in attrs:
                name = attr.get('name', '')
                value = attr.get('value', 0)
                raw = attr.get('raw', {}).get('value', 0)
                if name == 'Temperature_Celsius':
                    result['temperature'] = raw
                elif name == 'Power_On_Hours':
                    result['power_on_hours'] = raw
                elif name == 'Power_Cycle_Count':
                    result['power_cycles'] = raw
                elif name == 'Read_Error_Rate':
                    result['read_errors'] = raw
                elif name == 'Write_Error_Rate':
                    result['write_errors'] = raw
                elif name == 'Reallocated_Sector_Ct':
                    result['reallocated_sectors'] = raw
                elif name == 'Current_Pending_Sector':
                    result['pending_sectors'] = raw
                elif name == 'Offline_Uncorrectable':
                    result['uncorrectable_sectors'] = raw

        # NVMe поддержка
        if 'nvme_smart_health_information_log' in data:
            nvme = data['nvme_smart_health_information_log']
            result['temperature'] = nvme.get('temperature', result['temperature'])
            result['power_on_hours'] = nvme.get('power_on_hours', result['power_on_hours'])
            result['power_cycles'] = nvme.get('power_cycles', result['power_cycles'])

        return result

    def scan(self):
        """Сканирует все диски и собирает SMART-данные."""
        self.get_devices()
        for device in self.devices:
            print(f"🔍 Проверка {device}...")
            data = self.get_smart_data(device)
            if data:
                parsed = self.parse_attributes(data)
                if parsed:
                    self.smart_data[device] = parsed

    def print_table(self):
        """Выводит таблицу с данными."""
        if not self.smart_data:
            print(Fore.YELLOW + "Нет данных для отображения.")
            return

        print(Fore.CYAN + "\n📊 Состояние дисков:")
        print("┌" + "─" * 100 + "┐")
        print(f"│ {'Диск':<12} {'Модель':<20} {'Темп.':<8} {'Время':<10} "
              f"{'Ошибки':<8} {'Realloc':<8} {'Состояние':<12} │")
        print("├" + "─" * 100 + "┤")

        for device, data in self.smart_data.items():
            temp = f"{data.get('temperature', 'N/A')}°C" if data.get('temperature') else 'N/A'
            hours = f"{data.get('power_on_hours', 0)}h" if data.get('power_on_hours') else 'N/A'
            errors = data.get('read_errors', 0) or 0
            realloc = data.get('reallocated_sectors', 0) or 0
            status = data.get('smart_status', False)

            status_color = Fore.GREEN if status else Fore.RED
            status_text = "✅ OK" if status else "❌ FAIL"

            # Цвет для температуры
            temp_color = Fore.GREEN
            if data.get('temperature') and data['temperature'] > 50:
                temp_color = Fore.RED
            elif data.get('temperature') and data['temperature'] > 40:
                temp_color = Fore.YELLOW

            model = data.get('model', 'Unknown')[:20]
            print(f"│ {device:<12} {model:<20} {temp_color}{temp:<8}{Style.RESET_ALL} "
                  f"{hours:<10} {errors:<8} {realloc:<8} {status_color}{status_text:<12}{Style.RESET_ALL} │")

        print("└" + "─" * 100 + "┘")

    def save_json(self, filename='smart_report.json'):
        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(self.smart_data, f, indent=2, ensure_ascii=False)
        print(Fore.GREEN + f"💾 Сохранено JSON: {filename}")

    def save_csv(self, filename='smart_report.csv'):
        if not self.smart_data:
            return
        with open(filename, 'w', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            writer.writerow(['Device', 'Model', 'Serial', 'Temperature', 'PowerOnHours',
                             'ReadErrors', 'WriteErrors', 'ReallocatedSectors',
                             'PendingSectors', 'SMARTStatus'])
            for device, data in self.smart_data.items():
                writer.writerow([
                    device,
                    data.get('model', ''),
                    data.get('serial', ''),
                    data.get('temperature', ''),
                    data.get('power_on_hours', ''),
                    data.get('read_errors', ''),
                    data.get('write_errors', ''),
                    data.get('reallocated_sectors', ''),
                    data.get('pending_sectors', ''),
                    data.get('smart_status', False)
                ])
        print(Fore.GREEN + f"💾 Сохранено CSV: {filename}")

def main():
    print(Fore.CYAN + "💾 SMART Disk Monitor (Python)")
    monitor = SMARTMonitor()
    monitor.scan()
    monitor.print_table()

    if monitor.smart_data:
        monitor.save_json()
        monitor.save_csv()

if __name__ == "__main__":
    main()
