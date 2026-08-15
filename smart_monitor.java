// smart_monitor.java — Java версия

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.time.*;

public class smart_monitor {
    private static Map<String, Map<String, Object>> smartData = new LinkedHashMap<>();

    public static void main(String[] args) {
        System.out.println("\u001B[36m💾 SMART Disk Monitor (Java)\u001B[0m");

        List<String> devices = getDevices();
        for (String device : devices) {
            System.out.println("🔍 Проверка " + device + "...");
            Map<String, Object> data = getSmartData(device);
            if (data != null) {
                smartData.put(device, data);
            }
        }

        printTable();
        saveJSON("smart_report.json");
        saveCSV("smart_report.csv");
    }

    private static List<String> getDevices() {
        List<String> devices = new ArrayList<>();
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"smartctl", "--scan"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length > 0) {
                        devices.add(parts[0]);
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            System.out.println("\u001B[31m❌ smartctl не найден. Установите smartmontools.\u001B[0m");
            System.exit(1);
        }
        return devices;
    }

    private static Map<String, Object> getSmartData(String device) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"smartctl", "-j", "-a", device});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            // Простой парсинг JSON (без библиотеки)
            // Для реального проекта используйте Jackson или Gson
            Map<String, Object> result = new HashMap<>();
            String json = sb.toString();

            // Извлекаем модель
            String model = extractValue(json, "model_name");
            result.put("model", model != null ? model : "Unknown");
            String serial = extractValue(json, "serial_number");
            result.put("serial", serial != null ? serial : "Unknown");

            // SMART статус
            boolean smartStatus = json.contains("\"passed\":true");
            result.put("smart_status", smartStatus);

            // Температура
            String temp = extractValue(json, "\"temperature\"");
            if (temp != null) {
                try {
                    result.put("temperature", Integer.parseInt(temp));
                } catch (NumberFormatException e) {}
            }

            // Power on hours
            String hours = extractValue(json, "\"power_on_hours\"");
            if (hours != null) {
                try {
                    result.put("power_on_hours", Long.parseLong(hours));
                } catch (NumberFormatException e) {}
            }

            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static void printTable() {
        if (smartData.isEmpty()) {
            System.out.println("\u001B[33mНет данных для отображения.\u001B[0m");
            return;
        }

        System.out.println("\n\u001B[36m📊 Состояние дисков:\u001B[0m");
        System.out.println("┌" + "─".repeat(100) + "┐");
        System.out.printf("│ %-12s %-20s %-8s %-10s %-8s %-8s %-12s │\n",
                "Диск", "Модель", "Темп.", "Время", "Ошибки", "Realloc", "Состояние");
        System.out.println("├" + "─".repeat(100) + "┤");

        for (Map.Entry<String, Map<String, Object>> entry : smartData.entrySet()) {
            String device = entry.getKey();
            Map<String, Object> data = entry.getValue();

            String model = ((String) data.getOrDefault("model", "Unknown"));
            if (model.length() > 20) model = model.substring(0, 20);

            Object tempObj = data.get("temperature");
            String temp = tempObj != null ? tempObj + "°C" : "N/A";
            Object hoursObj = data.get("power_on_hours");
            String hours = hoursObj != null ? hoursObj + "h" : "N/A";

            String errors = "0";
            String realloc = "0";
            boolean status = (boolean) data.getOrDefault("smart_status", false);

            String statusText = status ? "\u001B[32m✅ OK\u001B[0m" : "\u001B[31m❌ FAIL\u001B[0m";

            String tempColor = "\u001B[32m";
            if (tempObj instanceof Integer && (Integer) tempObj > 50) {
                tempColor = "\u001B[31m";
            } else if (tempObj instanceof Integer && (Integer) tempObj > 40) {
                tempColor = "\u001B[33m";
            }

            System.out.printf("│ %-12s %-20s %s%-8s\u001B[0m %-10s %-8s %-8s %-12s │\n",
                    device, model, tempColor, temp, hours, errors, realloc, statusText);
        }
        System.out.println("└" + "─".repeat(100) + "┘");
    }

    private static void saveJSON(String filename) {
        try {
            String json = "[";
            int count = 0;
            for (Map.Entry<String, Map<String, Object>> entry : smartData.entrySet()) {
                if (count++ > 0) json += ",";
                json += "{\"device\":\"" + entry.getKey() + "\",";
                Map<String, Object> data = entry.getValue();
                for (Map.Entry<String, Object> e : data.entrySet()) {
                    json += "\"" + e.getKey() + "\":";
                    if (e.getValue() instanceof String) {
                        json += "\"" + e.getValue() + "\",";
                    } else {
                        json += e.getValue() + ",";
                    }
                }
                json = json.substring(0, json.length() - 1);
                json += "}";
            }
            json += "]";
            Files.write(Paths.get(filename), json.getBytes());
            System.out.println("\u001B[32m💾 Сохранено JSON: " + filename + "\u001B[0m");
        } catch (Exception e) {
            System.out.println("Ошибка сохранения JSON: " + e.getMessage());
        }
    }

    private static void saveCSV(String filename) {
        if (smartData.isEmpty()) return;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Device,Model,Serial,Temperature,PowerOnHours,SMARTStatus\n");
            for (Map.Entry<String, Map<String, Object>> entry : smartData.entrySet()) {
                Map<String, Object> data = entry.getValue();
                sb.append(entry.getKey()).append(",");
                sb.append(data.getOrDefault("model", "")).append(",");
                sb.append(data.getOrDefault("serial", "")).append(",");
                sb.append(data.getOrDefault("temperature", "")).append(",");
                sb.append(data.getOrDefault("power_on_hours", "")).append(",");
                sb.append(data.getOrDefault("smart_status", false)).append("\n");
            }
            Files.write(Paths.get(filename), sb.toString().getBytes());
            System.out.println("\u001B[32m💾 Сохранено CSV: " + filename + "\u001B[0m");
        } catch (Exception e) {
            System.out.println("Ошибка сохранения CSV: " + e.getMessage());
        }
    }
}
