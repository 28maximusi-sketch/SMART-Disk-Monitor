// smart_monitor.go — Go версия

package main

import (
	"encoding/csv"
	"encoding/json"
	"fmt"
	"os"
	"strconv"

	"github.com/anatol/smart.go"
)

type DiskInfo struct {
	Device              string
	Model               string
	Serial              string
	Temperature         int
	PowerOnHours        uint64
	PowerCycles         uint64
	ReadErrors          uint64
	WriteErrors         uint64
	ReallocatedSectors  uint64
	PendingSectors      uint64
	UncorrectableSectors uint64
	SMARTStatus         bool
}

func main() {
	fmt.Println("\x1b[36m💾 SMART Disk Monitor (Go)\x1b[0m")

	var disks []DiskInfo

	// Получаем список блочных устройств
	// Для простоты используем заранее известные устройства
	// В реальном проекте нужно использовать ghw или аналоги
	devices := []string{"/dev/sda", "/dev/sdb", "/dev/nvme0n1"}

	for _, dev := range devices {
		if _, err := os.Stat(dev); os.IsNotExist(err) {
			continue
		}

		fmt.Printf("🔍 Проверка %s...\n", dev)

		info := DiskInfo{Device: dev}

		// Открываем устройство через smart.go
		device, err := smart.Open(dev)
		if err != nil {
			fmt.Printf("⚠️  Не удалось открыть %s: %v\n", dev, err)
			continue
		}
		defer device.Close()

		// Получаем общие атрибуты
		attrs, err := device.ReadGenericAttributes()
		if err == nil {
			info.Temperature = attrs.Temperature
			info.PowerOnHours = attrs.PowerOnHours
			info.PowerCycles = attrs.PowerCycles
			info.ReadErrors = attrs.Read
			info.WriteErrors = attrs.Written
		}

		// Определяем тип устройства и получаем дополнительные данные
		switch d := device.(type) {
		case *smart.SataDevice:
			data, err := d.ReadSMARTData()
			if err == nil {
				if attr, ok := data.Attrs[5]; ok { // Reallocated_Sector_Ct
					info.ReallocatedSectors = attr.Raw
				}
				if attr, ok := data.Attrs[197]; ok { // Current_Pending_Sector
					info.PendingSectors = attr.Raw
				}
				if attr, ok := data.Attrs[198]; ok { // Offline_Uncorrectable
					info.UncorrectableSectors = attr.Raw
				}
			}
			info.SMARTStatus = true
		case *smart.NVMeDevice:
			sm, err := d.ReadSMART()
			if err == nil {
				info.Temperature = int(sm.Temperature)
				info.PowerOnHours = sm.PowerOnHours.Val[0]
				info.PowerCycles = sm.PowerCycles.Val[0]
				info.SMARTStatus = true
			}
		default:
			info.SMARTStatus = true
		}

		disks = append(disks, info)
	}

	// Вывод таблицы
	fmt.Println("\n\x1b[36m📊 Состояние дисков:\x1b[0m")
	fmt.Println("┌" + "─"*100 + "┐")
	fmt.Printf("│ %-12s %-20s %-8s %-10s %-8s %-8s %-12s │\n",
		"Диск", "Модель", "Темп.", "Время", "Ошибки", "Realloc", "Состояние")
	fmt.Println("├" + "─"*100 + "┤")

	for _, d := range disks {
		temp := "N/A"
		if d.Temperature > 0 {
			temp = fmt.Sprintf("%d°C", d.Temperature)
		}
		hours := "N/A"
		if d.PowerOnHours > 0 {
			hours = fmt.Sprintf("%dh", d.PowerOnHours)
		}
		errors := fmt.Sprintf("%d", d.ReadErrors)
		realloc := fmt.Sprintf("%d", d.ReallocatedSectors)

		status := "\x1b[32m✅ OK\x1b[0m"
		if !d.SMARTStatus {
			status = "\x1b[31m❌ FAIL\x1b[0m"
		}

		tempColor := "\x1b[32m"
		if d.Temperature > 50 {
			tempColor = "\x1b[31m"
		} else if d.Temperature > 40 {
			tempColor = "\x1b[33m"
		}

		fmt.Printf("│ %-12s %-20s %s%-8s\x1b[0m %-10s %-8s %-8s %-12s │\n",
			d.Device, d.Model[:min(20, len(d.Model))],
			tempColor, temp, hours, errors, realloc, status)
	}
	fmt.Println("└" + "─"*100 + "┘")

	// Сохранение JSON
	jsonData, _ := json.MarshalIndent(disks, "", "  ")
	os.WriteFile("smart_report.json", jsonData, 0644)
	fmt.Println("\x1b[32m💾 Сохранено JSON: smart_report.json\x1b[0m")
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
