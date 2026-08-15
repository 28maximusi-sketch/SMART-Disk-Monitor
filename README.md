💾 SMART Disk Monitor — диагностика здоровья дисков в реальном времени
«SMART — это не просто технология, это твой страховой полис для данных»

SMART Disk Monitor — это набор консольных утилит для мониторинга состояния жёстких дисков и SSD с использованием технологии S.M.A.R.T. (Self-Monitoring, Analysis and Reporting Technology).
Программа показывает ключевые атрибуты здоровья дисков: температуру, количество ошибок чтения/записи, время наработки, перераспределённые сектора и многое другое.

🚀 Особенности
🔍 Автоматическое обнаружение всех дисков в системе.

📊 Отображение ключевых SMART-атрибутов: температура, время работы, ошибки, перераспределённые сектора.

🎨 Цветовая индикация состояния здоровья (зелёный — OK, жёлтый — внимание, красный — критично).

📋 Вывод в удобной таблице с сортировкой.

💾 Экспорт результатов в JSON и CSV.

⏱️ Мониторинг в реальном времени с обновлением каждые N секунд.

🖥️ Кроссплатформенная поддержка: Linux, Windows, macOS.

🔧 Поддержка SATA, SAS и NVMe дисков.

🛠️ Установка и запуск
Для работы программ требуется установленный smartmontools (утилита smartctl).

OS	Команда установки
Linux (Debian/Ubuntu)	sudo apt install smartmontools
Linux (RHEL/CentOS)	sudo yum install smartmontools
macOS (Homebrew)	brew install smartmontools
Windows	Скачайте установщик с сайта smartmontools
Запуск
Для каждого языка — минимальные зависимости.

Язык	Зависимости	Команда запуска
Python	colorama (опционально)	python smart_monitor.py
Go	github.com/anatol/smart.go	go run smart_monitor.go
JavaScript	Node.js, node-diskmanager	node smart_monitor.js
Java	стандартная библиотека	javac smart_monitor.java && java smart_monitor
C#	Krugertech.IO.Smart (NuGet)	dotnet run
Rust	hdd или libatasmart	cargo run
Ruby	стандартная библиотека	ruby smart_monitor.rb
PHP	стандартная библиотека	php smart_monitor.php
📖 Пример использования
bash
$ python smart_monitor.py
Вывод:

text
💾 SMART Disk Monitor (Python)
🔍 Сканирование дисков...

📊 Состояние дисков:
┌──────────────────────────────────────────────────────────────────────────────┐
│ Диск        Модель              Темп.  Время   Ошибки  Realloc  Состояние   │
├──────────────────────────────────────────────────────────────────────────────┤
│ /dev/sda    Samsung SSD 860     32°C   12543h  0       0        ✅ OK       │
│ /dev/sdb    WDC WD40EFAX        41°C   8765h   23      5        ⚠️ Внимание  │
│ /dev/nvme0  Samsung 970 EVO     45°C   3421h   0       0        ✅ OK       │
└──────────────────────────────────────────────────────────────────────────────┘

💾 Сохранено: smart_report.json
💾 Сохранено: smart_report.csv
🤝 Вклад
Принимаются улучшения, новые языки, фичи.

📜 Лицензия
MIT — используйте свободно.

Автор: Ваш покорный слуга
