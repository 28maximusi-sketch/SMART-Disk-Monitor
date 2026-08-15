<?php
// smart_monitor.php — PHP версия

class SMARTMonitor {
    private $smartData = [];

    private function getDevices() {
        $devices = [];
        try {
            $output = shell_exec('smartctl --scan 2>/dev/null');
            if ($output === null) {
                echo "\033[31m❌ smartctl не найден. Установите smartmontools.\033[0m\n";
                exit(1);
            }
            $lines = explode("\n", trim($output));
            foreach ($lines as $line) {
                if (trim($line) !== '') {
                    $parts = preg_split('/\s+/', trim($line));
                    if (count($parts) > 0) {
                        $devices[] = $parts[0];
                    }
                }
            }
        } catch (Exception $e) {
            echo "\033[31m❌ Ошибка: " . $e->getMessage() . "\033[0m\n";
        }
        return $devices;
    }

    private function getSmartData($device) {
        try {
            $output = shell_exec("smartctl -j -a $device 2>/dev/null");
            if ($output === null) return null;
            $data = json_decode($output, true);
            return $this->parseAttributes($data);
        } catch (Exception $e) {
            return null;
        }
    }

    private function parseAttributes($data) {
        if (!$data) return null;

        $result = [
            'model' => $data['model_name'] ?? 'Unknown',
            'serial' => $data['serial_number'] ?? 'Unknown',
            'temperature' => null,
            'power_on_hours' => null,
            'read_errors' => null,
            'write_errors' => null,
            'reallocated_sectors' => null,
            'pending_sectors' => null,
            'smart_status' => $data['smart_status']['passed'] ?? false
        ];

        if (isset($data['ata_smart_attributes']['table'])) {
            foreach ($data['ata_smart_attributes']['table'] as $attr) {
                $name = $attr['name'] ?? '';
                $raw = $attr['raw']['value'] ?? null;
                switch ($name) {
                    case 'Temperature_Celsius': $result['temperature'] = $raw; break;
                    case 'Power_On_Hours': $result['power_on_hours'] = $raw; break;
                    case 'Reallocated_Sector_Ct': $result['reallocated_sectors'] = $raw; break;
                    case 'Current_Pending_Sector': $result['pending_sectors'] = $raw; break;
                }
            }
        }

        if (isset($data['nvme_smart_health_information_log'])) {
            $nvme = $data['nvme_smart_health_information_log'];
            $result['temperature'] = $result['temperature'] ?? $nvme['temperature'] ?? null;
            $result['power_on_hours'] = $result['power_on_hours'] ?? $nvme['power_on_hours'] ?? null;
        }

        return $result;
    }

    public function scan() {
        $devices = $this->getDevices();
        foreach ($devices as $device) {
            echo "🔍 Проверка $device...\n";
            $data = $this->getSmartData($device);
            if ($data) {
                $this->smartData[$device] = $data;
            }
        }
    }

    public function printTable() {
        if (empty($this->smartData)) {
            echo "\033[33mНет данных для отображения.\033[0m\n";
            return;
        }

        echo "\n\033[36m📊 Состояние дисков:\033[0m\n";
        echo "┌" . str_repeat("─", 100) . "┐\n";
        printf("│ %-12s %-20s %-8s %-10s %-8s %-8s %-12s │\n",
            "Диск", "Модель", "Темп.", "Время", "Ошибки", "Realloc", "Состояние");
        echo "├" . str_repeat("─", 100) . "┤\n";

        foreach ($this->smartData as $device => $data) {
            $temp = isset($data['temperature']) ? $data['temperature'] . "°C" : 'N/A';
            $hours = isset($data['power_on_hours']) ? $data['power_on_hours'] . "h" : 'N/A';
            $errors = $data['read_errors'] ?? 0;
            $realloc = $data['reallocated_sectors'] ?? 0;
            $status = $data['smart_status'] ? "\033[32m✅ OK\033[0m" : "\033[31m❌ FAIL\033[0m";

            $tempColor = "\033[32m";
            if (isset($data['temperature']) && $data['temperature'] > 50) {
                $tempColor = "\033[31m";
            } elseif (isset($data['temperature']) && $data['temperature'] > 40) {
                $tempColor = "\033[33m";
            }

            $model = substr($data['model'] ?? 'Unknown', 0, 20);
            printf("│ %-12s %-20s %s%-8s\033[0m %-10s %-8s %-8s %-12s │\n",
                $device, $model, $tempColor, $temp, $hours, $errors, $realloc, $status);
        }

        echo "└" . str_repeat("─", 100) . "┘\n";
    }

    public function saveJSON($filename = 'smart_report.json') {
        file_put_contents($filename, json_encode($this->smartData, JSON_PRETTY_PRINT));
        echo "\033[32m💾 Сохранено JSON: $filename\033[0m\n";
    }

    public function saveCSV($filename = 'smart_report.csv') {
        if (empty($this->smartData)) return;
        $fp = fopen($filename, 'w');
        fputcsv($fp, ['Device', 'Model', 'Serial', 'Temperature', 'PowerOnHours',
                      'ReadErrors', 'WriteErrors', 'ReallocatedSectors', 'PendingSectors', 'SMARTStatus']);
        foreach ($this->smartData as $device => $data) {
            fputcsv($fp, [
                $device,
                $data['model'] ?? '',
                $data['serial'] ?? '',
                $data['temperature'] ?? '',
                $data['power_on_hours'] ?? '',
                $data['read_errors'] ?? '',
                $data['write_errors'] ?? '',
                $data['reallocated_sectors'] ?? '',
                $data['pending_sectors'] ?? '',
                $data['smart_status'] ? 'true' : 'false'
            ]);
        }
        fclose($fp);
        echo "\033[32m💾 Сохранено CSV: $filename\033[0m\n";
    }
}

function main() {
    echo "\033[36m💾 SMART Disk Monitor (PHP)\033[0m\n";
    $monitor = new SMARTMonitor();
    $monitor->scan();
    $monitor->printTable();

    if (!empty($monitor->smartData)) {
        $monitor->saveJSON();
        $monitor->saveCSV();
    }
}

main();
