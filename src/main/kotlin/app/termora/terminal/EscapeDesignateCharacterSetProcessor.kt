package app.termora.terminal

import app.termora.terminal.EscapeDesignateCharacterSetProcessor.Companion.Graphic
import org.slf4j.LoggerFactory

class EscapeDesignateCharacterSetProcessor(terminal: Terminal, reader: TerminalReader) :
    AbstractProcessor(terminal, reader) {
    private val graphicCharacterSet get() = terminal.getTerminalModel().getData(DataKey.GraphicCharacterSet)

    /**
     * @see [Graphic]
     */
    private val graphic get() = terminal.getTerminalModel().getData(Graphic)

    companion object {
        /**
         * 要给哪个图形处理器设置字符集
         *
         * @see [EscapeSequenceProcessor]
         */
        internal val Graphic = DataKey(app.termora.terminal.Graphic::class)

        private val log = LoggerFactory.getLogger(EscapeDesignateCharacterSetProcessor::class.java)
    }


    override fun process(ch: Char): ProcessorState {
        return when (ch) {
            // United Kingdom (UK)
            'A' -> {
                graphicCharacterSet.designate(graphic, CharacterSet.UK)
                if (log.isDebugEnabled) {
                    log.debug("$graphic Character Set: United Kingdom (UK)")
                }
                TerminalState.READY
            }
            // United States (USASCII)
            'B' -> {
                graphicCharacterSet.designate(graphic, CharacterSet.USASCII)
                if (log.isDebugEnabled) {
                    log.debug("$graphic Character Set: United States (USASCII)")
                }
                TerminalState.READY
            }


            // Dutch, VT200.
            '4' -> {
                graphicCharacterSet.designate(graphic, CharacterSet.Dutch)
                if (log.isDebugEnabled) {
                    log.debug("$graphic Character Set: Dutch")
                }
                TerminalState.READY
            }


            // French, VT200.
            'R',
            'f' -> {
                graphicCharacterSet.designate(graphic, CharacterSet.French)
                if (log.isDebugEnabled) {
                    log.debug("$graphic Character Set: French")
                }
                TerminalState.READY
            }

            // German, VT200.
            'K' -> {
                graphicCharacterSet.designate(graphic, CharacterSet.German)
                if (log.isDebugEnabled) {
                    log.debug("$graphic Character Set: German")
                }
                TerminalState.READY
            }

            // Norwegian/Danish, VT200.
            'E', '6' -> {
                graphicCharacterSet.designate(graphic, CharacterSet.Norwegian_Danish)
                if (log.isDebugEnabled) {
                    log.debug("$graphic Character Set: Norwegian/Danish")
                }
                TerminalState.READY
            }

            // Swedish, VT200.
            'H', '7' -> {
                graphicCharacterSet.designate(graphic, CharacterSet.Swedish)
                if (log.isDebugEnabled) {
                    log.debug("$graphic Character Set: Swedish")
                }
                TerminalState.READY
            }


            // Swiss, VT200.
            '=' -> {
                graphicCharacterSet.designate(graphic, CharacterSet.Swiss)
                if (log.isDebugEnabled) {
                    log.debug("$graphic Character Set: Swiss")
                }
                TerminalState.READY
            }

            // DEC Supplemental, VT200.
            '<', 'U' -> {
                graphicCharacterSet.designate(graphic, CharacterSet.DECSupplemental)
                if (log.isDebugEnabled) {
                    log.debug("$graphic Character Set: DEC Supplemental")
                }
                TerminalState.READY
            }


            // Spanish, VT200.
            'Z' -> {
                graphicCharacterSet.designate(graphic, CharacterSet.Spanish)
                if (log.isDebugEnabled) {
                    log.debug("$graphic Character Set: Spanish")
                }
                TerminalState.READY
            }

            // Italian, VT200.
            'Y' -> {
                graphicCharacterSet.designate(graphic, CharacterSet.Italian)
                if (log.isDebugEnabled) {
                    log.debug("$graphic Character Set: Italian")
                }
                TerminalState.READY
            }

            // French Canadian, VT200.
            'Q',
            '9' -> {
                graphicCharacterSet.designate(graphic, CharacterSet.FrenchCanadian)
                if (log.isDebugEnabled) {
                    log.debug("$graphic Character Set: Finnish Canadian")
                }
                TerminalState.READY
            }

            // Finnish, VT20
            '5',
            'C' -> {
                graphicCharacterSet.designate(graphic, CharacterSet.Finnish)
                if (log.isDebugEnabled) {
                    log.debug("$graphic Character Set: Finnish")
                }
                TerminalState.READY
            }

            //  DEC Special Character and Line Drawing Set, VT100.
            '2',
            '0' -> {
                graphicCharacterSet.designate(graphic, CharacterSet.DECSpecialCharacter)
                if (log.isDebugEnabled) {
                    log.debug("$graphic Character Set: DEC Special Character and Line Drawing Set, VT100.")
                }
                TerminalState.READY
            }


            else -> {
                if (log.isWarnEnabled) {
                    log.warn("Unknown character set: $ch")
                }
                TerminalState.READY
            }
        }
    }
}