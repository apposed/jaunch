#include <objc/objc.h>
#include <objc/NSObjCRuntime.h>

const char* JAUNCH_EXE = "../../jaunch/jaunch";

void show_alert(const char *title, const char *message) {
	// Create an NSString from the C string
	id nsMessage = objc_msgSend((id)objc_getClass("NSString"), sel_registerName("stringWithUTF8String:"), message);

	// Create an NSAlert
	id alert = objc_msgSend((id)objc_getClass("NSAlert"), sel_registerName("alloc"));
	objc_msgSend(alert, sel_registerName("init"));
	objc_msgSend(alert, sel_registerName("setMessageText:"), nsMessage);

	// Run the alert modal
	objc_msgSend(alert, sel_registerName("runModal"));
}
