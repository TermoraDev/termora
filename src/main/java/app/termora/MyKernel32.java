package app.termora;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;

public interface MyKernel32 extends StdCallLibrary {

    MyKernel32 INSTANCE = Native.load("Kernel32", MyKernel32.class);
    WString INVARIANT_LOCALE = new WString("");

    int CP_ACP = 0; // System default ANSI code page

    int CompareStringEx(WString lpLocaleName,
                        int dwCmpFlags,
                        WString lpString1,
                        int cchCount1,
                        WString lpString2,
                        int cchCount2,
                        Pointer lpVersionInformation,
                        Pointer lpReserved,
                        int lParam);

    default int CompareStringEx(int dwCmpFlags,
                                String str1,
                                String str2) {
        return MyKernel32.INSTANCE
                .CompareStringEx(
                        INVARIANT_LOCALE,
                        dwCmpFlags,
                        new WString(str1),
                        str1.length(),
                        new WString(str2),
                        str2.length(),
                        Pointer.NULL,
                        Pointer.NULL,
                        0);
    }

    int MultiByteToWideChar(int CodePage, int dwFlags, byte[] lpMultiByteStr, int cbMultiByte, char[] lpWideCharStr, int cchWideChar);
}