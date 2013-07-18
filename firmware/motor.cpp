#include "motor.h"
#include <SwitecX25.h>

const int MOTOR_PIN_1 = 8;
const int MOTOR_PIN_2 = 7;
const int MOTOR_PIN_3 = 5;
const int MOTOR_PIN_4 = 6;
static int nextPos;
static int targetMotorPosition = 0;
static int currentMotorPosition = 0;

SwitecX25 motor1(STEPS, MOTOR_PIN_1, MOTOR_PIN_2, MOTOR_PIN_3, MOTOR_PIN_4);

void initMotor() {
    //Zero the position, bring needle to 50%, set to zero
    motorZero();
}

void updateMotorPosition() {
    long delta = getMotorDeltaFromTarget();
    if((delta) > 0) {
        motor1.stepUp();
        currentMotorPosition++;
    } else if(delta < 0){
        motor1.stepDown();
        currentMotorPosition--;
    }
}

void motorZero() {
    motor1.zero();
}

void motorStepUp() {
    motor1.stepUp();
}

void motorStepDown() {
    motor1.stepDown();
}

void setMotorTarget(long target) {
    targetMotorPosition = target;
}

long getMotorDeltaFromTarget() {
    return targetMotorPosition - currentMotorPosition;
}
