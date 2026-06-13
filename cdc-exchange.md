#

Прошивка, которая работает с Circuit Python:

```py
import time
import usb_cdc

serial = usb_cdc.data
while True:
    if serial.in_waiting > 0:
        
        has_completion = 0
        for by in serial.read(serial.in_waiting):
        
            if by == 116: # 0x74 - это CRC команды Identification протокола DSlip: 0xB4 0x00 0x81 0x00 0x74
                has_completion = 1
                break

            elif by == 199: # 0xC7 - это CRC команды Identification протокола CCNet: 0x02 0x03 0x06 0x37 0xFE 0xC7
                has_completion = 2
                break

        serial.reset_input_buffer()

        if has_completion > 0:

            if has_completion == 1:

                # Это код ответа на команду Idenitification протокола DSlip
                output_bytes = b"\xb4\x00\x81\x31\x00\x00\x00\x00\x00\x44\x32\x31\x30\x42\x41\x4d\x32\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x01\x00\x00\x00\x07\x00\x02\x01\x05\x00\x00\xff\x48"

            else:

                # Это код ответа на команду Idenitification протокола CCNET
                output_bytes = b"\x02\x03\x2f\x44\x32\x31\x30\x42\x41\x2d\x52\x55\x42\x00\x00\x00\x00\x00\x30\x30\x31\x2d\x30\x30\x30\x30\x31\x30\x37\x32\x00\x00\x00\x00\x00\x00\x00\x03\x17\x00\x00\x00\x00\x05\x2c\x33\xf9"
                
            serial.write(output_bytes)
            serial.flush()
    else:
        time.sleep(0.1)
```

Для проверки кода установил Python-библиотеку pyserial:

```shell
pip install pyserial
```

И запустил следующий код на ПК:

```py
import serial
import sys

def main():
    # Настройки порта
    port_name = 'COM5'
    baudrate = 115200  # Стандартная скорость; при необходимости измените
    timeout = 5      # Таймаут чтения в секундах

    # Данные для отправки (5 байт в шестнадцатеричном виде)
    data_to_send = bytes([0xB4, 0x00, 0x81, 0x00, 0x74])

    try:
        # Открываем последовательный порт
        with serial.Serial(
                port=port_name,
                baudrate=baudrate,
                timeout=timeout,
                bytesize=serial.EIGHTBITS,
                parity=serial.PARITY_NONE,
                stopbits=serial.STOPBITS_ONE
        ) as ser:
            print(f"Успешно подключено к порту {port_name}")

            # Отправляем данные
            ser.write(data_to_send)
            print(f"Отправлено {len(data_to_send)} байт: {data_to_send.hex()}")

            # Читаем ответ
            response = ser.read(1024)  # Читаем до 1024 байт (можно настроить)

            if response:
                print(f"Получено {len(response)} байт ответа:")
                print(f"В шестнадцатеричном виде: {response.hex()}")
                print(f"В виде байтов: {list(response)}")
                print(f"В текстовом виде (если возможно): {repr(response)}")
            else:
                print("Ответ не получен (таймаут)")

    except serial.SerialException as e:
        print(f"Ошибка работы с портом {port_name}: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"Неожиданная ошибка: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
```
