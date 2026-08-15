// smart_monitor.cs — C# версия

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text.Json;
using System.Linq;

class DiskInfo {
    public string Device { get; set; }
    public string Model { get; set; }
    public string Serial { get; set; }
    public int? Temperature { get; set; }
    public long? PowerOnHours { get; set; }
    public long? ReadErrors { get; set; }
    public long? WriteErrors { get; set; }
    public long? ReallocatedSectors { get; set; }
    public long? PendingSectors { get; set; }
    public bool SMARTStatus { get; set; }
}

class Program {
    static Dictionary<string, DiskInfo> smartData = new Dictionary<string, DiskInfo>();

    static void Main() {
        Console.WriteLine("\u001B[36m💾 SMART Disk Monitor (C#)\u001B[0m");

        var devices = GetDevices();
        foreach (var device in devices) {
            Console.WriteLine($"🔍 Проверка {device}...");
            var data = GetSmartData(device);
            if (data != null) {
                smartData[device] = data;
            }
        }

        PrintTable();
        SaveJSON("smart_report.json");
        SaveCSV("smart_report.csv");
    }

    static List<string> GetDevices() {
        var devices = new List<string>();
        try {
            var process = new Process {
                StartInfo = new ProcessStartInfo {
                    FileName = "smartctl",
                    Arguments = "--scan",
                    RedirectStandardOutput = true,
                    UseShellExecute = false
                }
            };
            process.Start();
            while (!process.StandardOutput.EndOfStream) {
                var line = process.StandardOutput.ReadLine();
                if (!string.IsNullOrWhiteSpace(line)) {
                    var parts = line.Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries);
                    if (parts.Length > 0) {
                        devices.Add(parts[0]);
                    }
                }
            }
            process.WaitForExit();
        } catch {
            Console.WriteLine("\u001B[31m❌ smartctl не найден. Установите smartmontools.\u001B[0m");
            Environment.Exit(1);
        }
        return devices;
    }

    static DiskInfo GetSmartData(string device) {
        try {
            var process = new Process {
                StartInfo = new ProcessStartInfo {
                    FileName = "smartctl",
                    Arguments = $"-j -a {device}",
                    RedirectStandardOutput = true,
                    UseShellExecute = false
                }
            };
            process.Start();
            string output = process.StandardOutput.ReadToEnd();
            process.WaitForExit();

            var json = JsonDocument.Parse(output);
            var root = json.RootElement;

            var info = new DiskInfo { Device = device };

            if (root.TryGetProperty("model_name", out var model)) {
                info.Model = model.GetString() ?? "Unknown";
            }
            if (root.TryGetProperty("serial_number", out var serial)) {
                info.Serial = serial.GetString() ?? "Unknown";
            }

            if (root.TryGetProperty("smart_status", out var status)) {
                if (status.TryGetProperty("passed", out var passed)) {
                    info.SMARTStatus = passed.GetBoolean();
                }
            }

            // Температура
            if (root.TryGetProperty("temperature", out var temp)) {
                info.Temperature = temp.GetInt32();
            }

            // Power on hours
            if (root.TryGetProperty("power_on_hours", out var hours)) {
                info.PowerOnHours = hours.GetInt64();
            }

            // Атрибуты ATA
            if (root.TryGetProperty("ata_smart_attributes", out var attrs)) {
                if (attrs.TryGetProperty("table", out var table)) {
                    foreach (var attr in table.EnumerateArray()) {
                        if (attr.TryGetProperty("name", out var name)) {
                            var attrName = name.GetString();
                            if (attr.TryGetProperty("raw", out var raw)) {
                                if (raw.TryGetProperty("value", out var rawValue)) {
                                    var val = rawValue.GetInt64();
                                    if (attrName == "Reallocated_Sector_Ct") info.ReallocatedSectors = val;
                                    else if (attrName == "Current_Pending_Sector") info.PendingSectors = val;
                                }
                            }
                        }
                    }
                }
            }

            return info;
        } catch {
            return null;
        }
    }

    static void PrintTable() {
        if (smartData.Count == 0) {
            Console.WriteLine("\u001B[33mНет данных для отображения.\u001B[0m");
            return;
        }

        Console.WriteLine("\n\u001B[36m📊 Состояние дисков:\u001B[0m");
        Console.WriteLine("┌" + new string('─', 100) + "┐");
        Console.WriteLine($"│ {"Диск",-12} {"Модель",-20} {"Темп.",-8} {"Время",-10} {"Ошибки",-8} {"Realloc",-8} {"Состояние",-12} │");
        Console.WriteLine("├" + new string('─', 100) + "┤");

        foreach (var kv in smartData) {
            var device = kv.Key;
            var info = kv.Value;

            var temp = info.Temperature.HasValue ? $"{info.Temperature}°C" : "N/A";
            var hours = info.PowerOnHours.HasValue ? $"{info.PowerOnHours}h" : "N/A";
            var errors = info.ReadErrors?.ToString() ?? "0";
            var realloc = info.ReallocatedSectors?.ToString() ?? "0";

            var status = info.SMARTStatus ? "\u001B[32m✅ OK\u001B[0m" : "\u001B[31m❌ FAIL\u001B[0m";

            var tempColor = "\u001B[32m";
            if (info.Temperature.HasValue && info.Temperature > 50) tempColor = "\u001B[31m";
            else if (info.Temperature.HasValue && info.Temperature > 40) tempColor = "\u001B[33m";

            var model = (info.Model ?? "Unknown").PadRight(20);
            if (model.Length > 20) model = model.Substring(0, 20);

            Console.WriteLine($"│ {device,-12} {model,-20} {tempColor}{temp,-8}\u001B[0m {hours,-10} {errors,-8} {realloc,-8} {status,-12} │");
        }
        Console.WriteLine("└" + new string('─', 100) + "┘");
    }

    static void SaveJSON(string filename) {
        var options = new JsonSerializerOptions { WriteIndented = true };
        var json = JsonSerializer.Serialize(smartData, options);
        File.WriteAllText(filename, json);
        Console.WriteLine($"\u001B[32m💾 Сохранено JSON: {filename}\u001B[0m");
    }

    static void SaveCSV(string filename) {
        if (smartData.Count == 0) return;
        var lines = new List<string>();
        lines.Add("Device,Model,Serial,Temperature,PowerOnHours,ReadErrors,WriteErrors,ReallocatedSectors,PendingSectors,SMARTStatus");
        foreach (var kv in smartData) {
            var info = kv.Value;
            lines.Add($"{kv.Key},{info.Model},{info.Serial},{info.Temperature},{info.PowerOnHours},{info.ReadErrors},{info.WriteErrors},{info.ReallocatedSectors},{info.PendingSectors},{info.SMARTStatus}");
        }
        File.WriteAllLines(filename, lines);
        Console.WriteLine($"\u001B[32m💾 Сохранено CSV: {filename}\u001B[0m");
    }
}
