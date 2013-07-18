#include "communication.h"
#include "motor.h"
#include "display.h"
#include "led.h"

String input = "";
const int SPEED_SLOPE = 1739;

void receiveCommands() {
    if(Serial.available()) {
        while(Serial.available() > 0){
            char c = Serial.read();
            if(c == ')' || c =='>'){
                parse_message(input);
                input = "";
            } else if(c == 'u') {
                motorStepUp();
            } else if(c == 'd') {
                motorStepDown();
            } else {
                input += c;
            }
        }
    }
}

void parse_message(String message) {
    for(unsigned int i = 0; i <= message.length(); i++) {
        if(message[i] == '(') {
            long motorVal = 10*(message[i+1]-'0') + (message[i+2]-'0');
            unsigned long motorPer = motorVal*1000/SPEED_SLOPE;
            setMotorTarget(STEPS * motorPer / 100);

            setDisplay(motorVal / 10, motorVal % 10);
        }


        if(message[i] == '<') {
            int rgbValue = 100*(message[i+1] -'0') + 10*(message[i+2]-'0') + (message[i+3]-'0');
            setLED(rgbValue, 255);
        }
    }
}
