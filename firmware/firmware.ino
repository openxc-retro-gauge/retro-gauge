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

void setup(void) {
    Serial.begin(9600);
    Serial.print("Go time!");

    initMotor();

    initDisplay(DISPLAY_DATA_PIN, DISPLAY_CLOCK_PIN, DISPLAY_LATCH_PIN);
    initLEDs(LED_RED_PIN, LED_GREEN_PIN, LED_BLUE_PIN);

    nextUpdate = micros();
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
