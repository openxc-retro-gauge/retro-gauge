#include "led.h"
#include "display.h"
#include "motor.h"
#include "communication.h"

const int LED_RED_PIN = 11;
const int LED_GREEN_PIN = 9;
const int LED_BLUE_PIN = 10;

const int DISPLAY_DATA_PIN  = 4;  // Pin connected to Pin 14 of 74HC595
const int DISPLAY_CLOCK_PIN = 2;  // Pin connected to Pin 11 of 74HC595
const int DISPLAY_LATCH_PIN = 3;  // Pin connected to Pin 12 of 74HC595

unsigned long nextUpdate;

void selfTest() {
    Serial.println("Beginning self-test");

    Serial.println("Testing motor...");
    setMotorTarget(STEPS);
    delay(500);

    Serial.println("Testing display...");
    for(int i = 0; i < 10; i++) {
        setDisplay(i, i);
        delay(200);
    }
    clearDisplay();

    Serial.println("Testing LEDs...");
    for(int hue = 0; hue < 260; hue++) {
        for(int saturation = 0; saturation < 256; saturation++) {
            setLED(hue, saturation, 255);
        }
    }

    Serial.println("Self-test complete");
}

void setup(void) {
    Serial.begin(9600);
    Serial.println("Retro Gauge initializing...");

    initMotor();

    initDisplay(DISPLAY_DATA_PIN, DISPLAY_CLOCK_PIN, DISPLAY_LATCH_PIN);
    initLEDs(LED_RED_PIN, LED_GREEN_PIN, LED_BLUE_PIN);

    selfTest();

    nextUpdate = micros();
    Serial.println("Gauge initialized.");
}

void loop() {
    long delta = getMotorDeltaFromTarget();
    if(micros() > nextUpdate) {
        updateMotorPosition();

        if(delta != 0) {
            nextUpdate = micros() + 400 + 279841 / abs(delta);
        }
    }

    receiveCommands();
}
