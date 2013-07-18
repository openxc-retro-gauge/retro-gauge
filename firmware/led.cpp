#include "led.h"
#include "Arduino.h"

int ledRedPin;
int ledGreenPin;
int ledBluePin;

void initLEDs(int redPin, int greenPin, int bluePin) {
    pinMode(redPin, OUTPUT);
    pinMode(bluePin, OUTPUT);
    pinMode(greenPin, OUTPUT);
    ledRedPin = redPin;
    ledGreenPin = greenPin;
    ledBluePin = bluePin;
}

void getRGB(int hue, int sat, int val, int colors[3]) {
  // hue: 0-259, sat: 0-255, val (lightness): 0-255
  int r = 0, g = 0, b = 0, base = 0;

  if(sat == 0) { // Achromatic color (gray).
    colors[0]=val;
    colors[1]=val;
    colors[2]=val;
  }
  else  {
    base = ((255 - sat) * val)>>8;
    switch(hue/60) {
    case 0:
      r = val;
      g = (((val-base)*hue)/60)+base;
      b = base;
      break;
    case 1:
      r = (((val-base)*(60-(hue%60)))/60)+base;
      g = val;
      b = base;
      break;
    case 2:
      r = base;
      g = val;
      b = (((val-base)*(hue%60))/60)+base;
      break;
    case 3:
      r = base;
      g = (((val-base)*(60-(hue%60)))/60)+base;
      b = val;
      break;
    case 4:
      r = (((val-base)*(hue%60))/60)+base;
      g = base;
      b = val;
      break;
    case 5:
      r = val;
      g = base;
      b = (((val-base)*(60-(hue%60)))/60)+base;
      break;
    }
    colors[0]=r;
    colors[1]=g;
    colors[2]=b;
  }
}

void ledWrite(int r, int g, int b){
  analogWrite(ledRedPin, 255-r);
  analogWrite(ledGreenPin, 255-g);
  analogWrite(ledBluePin, 255-b);
}

void setLED(int hue, int l){
  int col[3] = {0, 0, 0};
  getRGB(hue, 255, l, col);
  ledWrite(col[0], col[1], col[2]);
}
