# smart_monitor.rb — Ruby версия

require 'json'
require 'csv'
require 'open3'

class SMARTMonitor
  def initialize
    @smart_data = {}
  end

  def get_devices
    devices = []
    begin
      output, _ = Open3.capture2('smartctl', '--scan')
      output.each_line do |line|
        if line.strip != ''
          parts = line.strip.split
          devices << parts[0] if parts.any?
        end
      end
    rescue Errno::ENOENT
      puts "\e[31m❌ smartctl не найден. Установите smartmontools.\e[0m"
      exit 1
    end
    devices
  end

  def get_smart_data(device)
    begin
      output, _ = Open3.capture2('smartctl', '-j', '-a', device)
      data = JSON.parse(output)
      parse_attributes(data)
    rescue
      nil
    end
  end

  def parse_attributes(data)
    return nil unless data

    result = {
      model: data['model_name'] || 'Unknown',
      serial: data['serial_number'] || 'Unknown',
      temperature: nil,
      power_on_hours: nil,
      read_errors: nil,
      write_errors: nil,
      reallocated_sectors: nil,
      pending_sectors: nil,
      smart_status: data.dig('smart_status', 'passed') || false
    }

    if data['ata_smart_attributes']
      data['ata_smart_attributes']['table']&.each do |attr|
        name = attr['name']
        raw = attr.dig('raw', 'value')
        case name
        when 'Temperature_Celsius' then result[:temperature] = raw
        when 'Power_On_Hours' then result[:power_on_hours] = raw
        when 'Reallocated_Sector_Ct' then result[:reallocated_sectors] = raw
        when 'Current_Pending_Sector' then result[:pending_sectors] = raw
        end
      end
    end

    if data['nvme_smart_health_information_log']
      nvme = data['nvme_smart_health_information_log']
      result[:temperature] ||= nvme['temperature']
      result[:power_on_hours] ||= nvme['power_on_hours']
    end

    result
  end

  def scan
    devices = get_devices
    devices.each do |device|
      puts "🔍 Проверка #{device}..."
      data = get_smart_data(device)
      @smart_data[device] = data if data
    end
  end

  def print_table
    if @smart_data.empty?
      puts "\e[33mНет данных для отображения.\e[0m"
      return
    end

    puts "\n\e[36m📊 Состояние дисков:\e[0m"
    puts "┌" + "─" * 100 + "┐"
    puts "│ #{'Диск'.ljust(12)} #{'Модель'.ljust(20)} #{'Темп.'.ljust(8)} #{'Время'.ljust(10)} #{'Ошибки'.ljust(8)} #{'Realloc'.ljust(8)} #{'Состояние'.ljust(12)} │"
    puts "├" + "─" * 100 + "┤"

    @smart_data.each do |device, data|
      temp = data[:temperature] ? "#{data[:temperature]}°C" : 'N/A'
      hours = data[:power_on_hours] ? "#{data[:power_on_hours]}h" : 'N/A'
      errors = data[:read_errors] || 0
      realloc = data[:reallocated_sectors] || 0
      status = data[:smart_status] ? "\e[32m✅ OK\e[0m" : "\e[31m❌ FAIL\e[0m"

      temp_color = "\e[32m"
      if data[:temperature] && data[:temperature] > 50
        temp_color = "\e[31m"
      elsif data[:temperature] && data[:temperature] > 40
        temp_color = "\e[33m"
      end

      model = data[:model].to_s[0...20].ljust(20)
      puts "│ #{device.ljust(12)} #{model} #{temp_color}#{temp.ljust(8)}\e[0m #{hours.ljust(10)} #{errors.to_s.ljust(8)} #{realloc.to_s.ljust(8)} #{status.ljust(12)} │"
    end

    puts "└" + "─" * 100 + "┘"
  end

  def save_json(filename = 'smart_report.json')
    File.write(filename, JSON.pretty_generate(@smart_data))
    puts "\e[32m💾 Сохранено JSON: #{filename}\e[0m"
  end

  def save_csv(filename = 'smart_report.csv')
    return if @smart_data.empty?
    CSV.open(filename, 'w') do |csv|
      csv << ['Device', 'Model', 'Serial', 'Temperature', 'PowerOnHours',
              'ReadErrors', 'WriteErrors', 'ReallocatedSectors', 'PendingSectors', 'SMARTStatus']
      @smart_data.each do |device, data|
        csv << [
          device,
          data[:model],
          data[:serial],
          data[:temperature],
          data[:power_on_hours],
          data[:read_errors],
          data[:write_errors],
          data[:reallocated_sectors],
          data[:pending_sectors],
          data[:smart_status]
        ]
      end
    end
    puts "\e[32m💾 Сохранено CSV: #{filename}\e[0m"
  end
end

def main
  puts "\e[36m💾 SMART Disk Monitor (Ruby)\e[0m"
  monitor = SMARTMonitor.new
  monitor.scan
  monitor.print_table

  unless monitor.smart_data.empty?
    monitor.save_json
    monitor.save_csv
  end
end

main if __FILE__ == $0
