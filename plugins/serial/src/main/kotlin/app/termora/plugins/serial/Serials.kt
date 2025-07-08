package app.termora.plugins.serial

import app.termora.Host
import app.termora.SerialCommFlowControl
import app.termora.SerialCommParity
import com.fazecast.jSerialComm.SerialPort

object Serials {
    fun openPort(host: Host): SerialPort {
        val serialComm = host.options.serialComm
        val serialPort = SerialPort.getCommPort(serialComm.port)
        serialPort.setBaudRate(serialComm.baudRate)
        serialPort.setNumDataBits(serialComm.dataBits)

        when (serialComm.parity) {
            SerialCommParity.None -> serialPort.setParity(SerialPort.NO_PARITY)
            SerialCommParity.Mark -> serialPort.setParity(SerialPort.MARK_PARITY)
            SerialCommParity.Even -> serialPort.setParity(SerialPort.EVEN_PARITY)
            SerialCommParity.Odd -> serialPort.setParity(SerialPort.ODD_PARITY)
            SerialCommParity.Space -> serialPort.setParity(SerialPort.SPACE_PARITY)
        }

        when (serialComm.stopBits) {
            "1" -> serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT)
            "1.5" -> serialPort.setNumStopBits(SerialPort.ONE_POINT_FIVE_STOP_BITS)
            "2" -> serialPort.setNumStopBits(SerialPort.TWO_STOP_BITS)
        }

        when (serialComm.flowControl) {
            SerialCommFlowControl.None -> serialPort.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
            SerialCommFlowControl.RTS_CTS -> serialPort.setFlowControl(SerialPort.FLOW_CONTROL_RTS_ENABLED or SerialPort.FLOW_CONTROL_CTS_ENABLED)
            SerialCommFlowControl.XON_XOFF -> serialPort.setFlowControl(SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED or SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED)
        }

        if (!serialPort.openPort()) {
            throw IllegalStateException("Open serial port [${serialComm.port}] failed")
        }

        return serialPort
    }
}