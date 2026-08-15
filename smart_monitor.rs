// smart_monitor.rs — Rust версия

use std::process::Command;
use std::fs;
use std::collections::HashMap;
use serde_json::Value;
use colored::*;

struct DiskInfo {
    device: String,
    model: String,
    serial: String,
    temperature: Option<i64>,
    power_on_hours: Option<i64>,
    read_errors: Option<i64>,
    write_errors: Option<i64>,
    reallocated_sectors: Option<i64>,
    pending_sectors: Option<i64>,
    smart_status: bool,
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    println!("{}", "💾 SMART Disk Monitor (Rust)".cyan());

    let devices = get_devices()?;
    let mut smart_data: HashMap<String, DiskInfo> = HashMap::new();

    for device in devices {
        println!("🔍 Проверка {}...", device);
        if let Some(info) = get_smart_data(&device)? {
            smart_data.insert(device, info);
        }
    }

    print_table(&smart_data);
    save_json(&smart_data, "smart_report.json")?;
    save_csv(&smart_data, "smart_report.csv")?;

    Ok(())
}

fn get_devices() -> Result<Vec<String>, Box<dyn std::error::Error>> {
    let output = Command::new("smartctl")
        .arg("--scan")
        .output()?;

    if !output.status.success() {
        eprintln!("{}", "❌ smartctl не найден. Установите smartmontools.".red());
        std::process::exit(1);
    }

    let stdout = String::from_utf8(output.stdout)?;
    let mut devices = Vec::new();

    for line in stdout.lines() {
        if !line.trim().is_empty() {
            let parts: Vec<&str> = line.split_whitespace().collect();
            if !parts.is_empty() {
                devices.push(parts[0].to_string());
            }
        }
    }

    Ok(devices)
}

fn get_smart_data(device: &str) -> Result<Option<DiskInfo>, Box<dyn std::error::Error>> {
    let output = Command::new("smartctl")
        .args(&["-j", "-a", device])
        .output()?;

    if !output.status.success() {
        return Ok(None);
    }

    let stdout = String::from_utf8(output.stdout)?;
    let json: Value = serde_json::from_str(&stdout)?;

    let mut info = DiskInfo {
        device: device.to_string(),
        model: "Unknown".to_string(),
        serial: "Unknown".to_string(),
        temperature: None,
        power_on_hours: None,
        read_errors: None,
        write_errors: None,
        reallocated_sectors: None,
        pending_sectors: None,
        smart_status: false,
    };

    if let Some(model) = json.get("model_name").and_then(|v| v.as_str()) {
        info.model = model.to_string();
    }

    if let Some(serial) = json.get("serial_number").and_then(|v| v.as_str()) {
        info.serial = serial.to_string();
    }

    if let Some(status) = json.get("smart_status") {
        if let Some(passed) = status.get("passed").and_then(|v| v.as_bool()) {
            info.smart_status = passed;
        }
    }

    if let Some(temp) = json.get("temperature").and_then(|v| v.as_i64()) {
        info.temperature = Some(temp);
    }

    if let Some(hours) = json.get("power_on_hours").and_then(|v| v.as_i64()) {
        info.power_on_hours = Some(hours);
    }

    // ATA атрибуты
    if let Some(attrs) = json.get("ata_smart_attributes") {
        if let Some(table) = attrs.get("table").and_then(|v| v.as_array()) {
            for attr in table {
                if let Some(name) = attr.get("name").and_then(|v| v.as_str()) {
                    if let Some(raw) = attr.get("raw") {
                        if let Some(value) = raw.get("value").and_then(|v| v.as_i64()) {
                            match name {
                                "Reallocated_Sector_Ct" => info.reallocated_sectors = Some(value),
                                "Current_Pending_Sector" => info.pending_sectors = Some(value),
                                _ => {}
                            }
                        }
                    }
                }
            }
        }
    }

    Ok(Some(info))
}

fn print_table(smart_data: &HashMap<String, DiskInfo>) {
    if smart_data.is_empty() {
        println!("{}", "Нет данных для отображения.".yellow());
        return;
    }

    println!("\n{}", "📊 Состояние дисков:".cyan());
    println!("┌{}┐", "─".repeat(100));
    println!("│ {:<12} {:<20} {:<8} {:<10} {:<8} {:<8} {:<12} │",
        "Диск", "Модель", "Темп.", "Время", "Ошибки", "Realloc", "Состояние");
    println!("├{}┤", "─".repeat(100));

    for (device, info) in smart_data {
        let temp = info.temperature.map_or("N/A".to_string(), |t| format!("{}°C", t));
        let hours = info.power_on_hours.map_or("N/A".to_string(), |h| format!("{}h", h));
        let errors = info.read_errors.unwrap_or(0).to_string();
        let realloc = info.reallocated_sectors.unwrap_or(0).to_string();

        let status = if info.smart_status {
            "✅ OK".green().to_string()
        } else {
            "❌ FAIL".red().to_string()
        };

        let temp_color = if info.temperature.map_or(false, |t| t > 50) {
            temp.red()
        } else if info.temperature.map_or(false, |t| t > 40) {
            temp.yellow()
        } else {
            temp.normal()
        };

        let model = if info.model.len() > 20 {
            info.model[..20].to_string()
        } else {
            info.model.clone()
        };

        println!("│ {:<12} {:<20} {:<8} {:<10} {:<8} {:<8} {:<12} │",
            device, model, temp_color, hours, errors, realloc, status);
    }

    println!("└{}┘", "─".repeat(100));
}

fn save_json(smart_data: &HashMap<String, DiskInfo>, filename: &str) -> Result<(), Box<dyn std::error::Error>> {
    let json = serde_json::to_string_pretty(smart_data)?;
    fs::write(filename, json)?;
    println!("{}", format!("💾 Сохранено JSON: {}", filename).green());
    Ok(())
}

fn save_csv(smart_data: &HashMap<String, DiskInfo>, filename: &str) -> Result<(), Box<dyn std::error::Error>> {
    if smart_data.is_empty() { return Ok(()); }

    let mut csv = String::from("Device,Model,Serial,Temperature,PowerOnHours,ReadErrors,WriteErrors,ReallocatedSectors,PendingSectors,SMARTStatus\n");
    for (device, info) in smart_data {
        csv.push_str(&format!("{},{},{},{},{},{},{},{},{},{}\n",
            device,
            info.model,
            info.serial,
            info.temperature.map_or("".to_string(), |t| t.to_string()),
            info.power_on_hours.map_or("".to_string(), |h| h.to_string()),
            info.read_errors.map_or("".to_string(), |e| e.to_string()),
            info.write_errors.map_or("".to_string(), |e| e.to_string()),
            info.reallocated_sectors.map_or("".to_string(), |e| e.to_string()),
            info.pending_sectors.map_or("".to_string(), |e| e.to_string()),
            info.smart_status
        ));
    }

    fs::write(filename, csv)?;
    println!("{}", format!("💾 Сохранено CSV: {}", filename).green());
    Ok(())
}
