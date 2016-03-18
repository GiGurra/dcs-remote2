package se.gigurra.dcs.remote.keyboard

case class LLWindowsKeyboardEvent(wparInt: Int, flags: Int, scanCode: Int, vkCode: Int) {
  def state: Int = if (isKeyDown) 1 else 0
  def isKeyDown: Boolean = (flags & 0x80) == 0
  def isRepeat: Boolean = isKeyDown && KeyInput.isKeyDown(vkCode)
  override def toString: String = {
    s"Key $vkCode ${if (isKeyDown) if (isRepeat) "repeat" else "down" else "up"}"
  }
}

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.{Kernel32, User32}
import com.sun.jna.platform.win32.WinDef.{LRESULT, WPARAM}
import com.sun.jna.platform.win32.WinUser.{KBDLLHOOKSTRUCT, LowLevelKeyboardProc, MSG, WH_KEYBOARD_LL}
import se.gigurra.serviceutils.twitter.logging.Logging

object KeyInput extends Logging {

  val VK_SHIFT = 0x10
  val VK_CONTROL = 0x11
  val VK_ALT = 0x12

  def isShiftDown: Boolean = isKeyDown(VK_SHIFT)
  def isControlDown: Boolean = isKeyDown(VK_CONTROL)
  def isAltDown: Boolean = isKeyDown(VK_ALT)


  def isKeyDown(key: Int): Boolean = {
    (User32.INSTANCE.GetAsyncKeyState(key) & 0x8000) != 0
  }

  def enterKeyboardHookMessageLoop(listener: LLWindowsKeyboardEvent => Unit): Unit = {

    val lpfn = new LowLevelKeyboardProc {

      val pointerValueField = classOf[Pointer].getDeclaredField("peer")
      pointerValueField.setAccessible(true)
      def getPeer(pointer: Pointer): Long = pointerValueField.get(pointer).asInstanceOf[Long]

      override def callback(nCode: Int, wPar: WPARAM, lp: KBDLLHOOKSTRUCT): LRESULT = {
        val event = LLWindowsKeyboardEvent(wPar.intValue, lp.flags, lp.scanCode, lp.vkCode)
        listener.apply(event)
        User32.INSTANCE.CallNextHookEx(null, nCode, wPar, new LPARAM(getPeer(lp.getPointer)))
      }
    }

    val hModule = Kernel32.INSTANCE.GetModuleHandle(null)
    val hHook = User32.INSTANCE.SetWindowsHookEx(WH_KEYBOARD_LL, lpfn, hModule, 0)
    if (hHook == null) {
      logger.fatal("Failed to create keyboard hook, bailing!")
      System.exit(1)
    }

    val msg = new MSG()
    var quit = false
    while (!quit) {
      val result = User32.INSTANCE.GetMessage(msg, null, 0, 0)
      if (result == -1 || result == 0) {
        quit = true
      } else {
        User32.INSTANCE.TranslateMessage(msg)
        User32.INSTANCE.DispatchMessage(msg)
      }
    }

    User32.INSTANCE.UnhookWindowsHookEx(hHook)
  }

}
