#include "display.h"

int displayDataPin;
int displayClockPin;
int displayLatchPin;

const byte NONE = B00000000;

const byte LED_CHAR_SET[10] = {
    B11111100,B01100000,B11011010,B11110010, //0,1,2,3
    B01100110,B10110110,B10111110,B11100000, //4,5,6,7
    B11111110,B11100110};                    //8,9

void initDisplay(int dataPin, int clockPin, int latchPin) {
    pinMode(latchPin, OUTPUT);
    pinMode(clockPin, OUTPUT);
    pinMode(dataPin, OUTPUT);
    displayDataPin = dataPin;
    displayClockPin = clockPin;
    displayLatchPin = latchPin;
    clearDisplay(dataPin, clockPin, latchPin);
}

void clearDisplay(int dataPin, int clockPin, int latchPin) {
  //Prepare 595 to received data
  digitalWrite(latchPin, LOW);
  //Shift data to 595
  shiftOut(dataPin, clockPin, LSBFIRST, NONE);
  shiftOut(dataPin,clockPin, LSBFIRST, NONE);
  //Set latch to high to send data
  digitalWrite(latchPin, HIGH);
}

void setDisplay(int display1, int display2) {
  //Prepare 595 to received data
  digitalWrite(displayLatchPin, LOW);
  //Shift data to 595
  shiftOut(displayDataPin, displayClockPin, LSBFIRST, LED_CHAR_SET[display2]);
  shiftOut(displayDataPin, displayClockPin, LSBFIRST, LED_CHAR_SET[display1]);
  //Set latch to high to send data
  digitalWrite(displayLatchPin, HIGH);
}
