// smart_monitor.js — JavaScript версия

const { exec } = require('child_process');
const fs = require('fs');
const util = require('util');

const execPromise = util.promisify(exec);

class SMARTMonitor {
    constructor() {
        this.devices = [];
        this.smartData = {};
    }

    async getDevices() {
        try {
            const { stdout } = await execPromise('smartctl --scan');
            const lines = stdout.trim().split('\n');
            for (const line of lines) {
                if (line) {
                    const parts = line.split(/\s+/);
                    if (parts.length > 0) {
                        this.devices.push(parts[0]);
                    }
                }
            }
        } catch (error) {
            console.error('\x1b[31m❌ smartctl не найден. Установите smartmontools.\x1b[0m');
            process.exit(1);
        }
        return this.devices;
    }

    async getSmartData(device) {
        try {
            const { stdout } = await execPromise(`smartctl -j -a ${device}`);
            return JSON.parse(stdout);
        } catch (error) {
            return null;
        }
    }

    parseAttributes(data) {
        if (!data) return null;

        const result = {
            model: data.model_name || 'Unknown',
            serial: data.serial_number || 'Unknown',
            firmware: data.firmware_version || 'Unknown',
            smart_supported: data.smart_supported || false,
            smart_enabled: data.smart_enabled || false,
            smart_status: data.smart_status?.passed || false,
            temperature: null,
            power_on_hours: null,
            power_cycles: null,
            read_errors: null,
            write_errors: null,
            reallocated_sectors: null,
            pending_sectors: null,
            uncorrectable_sectors: null,
        };

        if (data.ata_smart_attributes) {
            const attrs = data.ata_smart_attributes.table || [];
            for (const attr of attrs) {
                const name = attr.name || '';
                const raw = attr.raw?.value || 0;
                if (name === 'Temperature_Celsius') result.temperature = raw;
                else if (name === 'Power_On_Hours') result.power_on_hours = raw;
                else if (name === 'Power_Cycle_Count') result.power_cycles = raw;
                else if (name === 'Read_Error_Rate') result.read_errors = raw;
                else if (name === 'Write_Error_Rate') result.write_errors = raw;
                else if (name === 'Reallocated_Sector_Ct') result.reallocated_sectors = raw;
                else if (name === 'Current_Pending_Sector') result.pending_sectors = raw;
                else if (name === 'Offline_Uncorrectable') result.uncorrectable_sectors = raw;
            }
        }

        if (data.nvme_smart_health_information_log) {
            const nvme = data.nvme_smart_health_information_log;
            result.temperature = nvme.temperature || result.temperature;
            result.power_on_hours = nvme.power_on_hours || result.power_on_hours;
            result.power_cycles = nvme.power_cycles || result.power_cycles;
        }

        return result;
    }

    async scan() {
        await this.getDevices();
        for (const device of this.devices) {
            console.log(`🔍 Проверка ${device}...`);
            const data = await this.getSmartData(device);
            if (data) {
                const parsed = this.parseAttributes(data);
                if (parsed) {
                    this.smartData[device] = parsed;
                }
            }
        }
    }

    printTable() {
        const data = this.smartData;
        if (Object.keys(data).length === 0) {
            console.log('\x1b[33mНет данных для отображения.\x1b[0m');
            return;
        }

        console.log('\x1b[36m\n📊 Состояние дисков:\x1b[0m');
        console.log('┌' + '─'.repeat(100) + '┐');
        console.log(`│ ${'Диск'.padEnd(12)} ${'Модель'.padEnd(20)} ${'Темп.'.padEnd(8)} ${'Время'.padEnd(10)} ${'Ошибки'.padEnd(8)} ${'Realloc'.padEnd(8)} ${'Состояние'.padEnd(12)} │`);
        console.log('├' + '─'.repeat(100) + '┤');

        for (const [device, info] of Object.entries(data)) {
            const temp = info.temperature ? `${info.temperature}°C` : 'N/A';
            const hours = info.power_on_hours ? `${info.power_on_hours}h` : 'N/A';
            const errors = info.read_errors || 0;
            const realloc = info.reallocated_sectors || 0;
            const status = info.smart_status ? '\x1b[32m✅ OK\x1b[0m' : '\x1b[31m❌ FAIL\x1b[0m';

            let tempColor = '\x1b[32m';
            if (info.temperature > 50) tempColor = '\x1b[31m';
            else if (info.temperature > 40) tempColor = '\x1b[33m';

            const model = (info.model || 'Unknown').slice(0, 20);
            console.log(`│ ${device.padEnd(12)} ${model.padEnd(20)} ${tempColor}${temp.padEnd(8)}\x1b[0m ${hours.padEnd(10)} ${String(errors).padEnd(8)} ${String(realloc).padEnd(8)} ${status.padEnd(12)} │`);
        }
        console.log('└' + '─'.repeat(100) + '┘');
    }

    saveJSON(filename = 'smart_report.json') {
        fs.writeFileSync(filename, JSON.stringify(this.smartData, null, 2));
        console.log(`\x1b[32m💾 Сохранено JSON: ${filename}\x1b[0m`);
    }

    saveCSV(filename = 'smart_report.csv') {
        const data = this.smartData;
        if (Object.keys(data).length === 0) return;
        let csv = 'Device,Model,Serial,Temperature,PowerOnHours,ReadErrors,WriteErrors,ReallocatedSectors,PendingSectors,SMARTStatus\n';
        for (const [device, info] of Object.entries(data)) {
            csv += `${device},${info.model || ''},${info.serial || ''},${info.temperature || ''},${info.power_on_hours || ''},${info.read_errors || ''},${info.write_errors || ''},${info.reallocated_sectors || ''},${info.pending_sectors || ''},${info.smart_status || false}\n`;
        }
        fs.writeFileSync(filename, csv);
        console.log(`\x1b[32m💾 Сохранено CSV: ${filename}\x1b[0m`);
    }
}

async function main() {
    console.log('\x1b[36m💾 SMART Disk Monitor (JavaScript)\x1b[0m');
    const monitor = new SMARTMonitor();
    await monitor.scan();
    monitor.printTable();

    if (Object.keys(monitor.smartData).length > 0) {
        monitor.saveJSON();
        monitor.saveCSV();
    }
}

main().catch(console.error);
