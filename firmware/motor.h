// standard X25.168 range 315 degrees at 1/10 degree steps
const int STEPS = 315 * 3;

void initMotor();
void updateMotorPosition();
void motorZero();
void motorStepUp();
void motorStepDown();
void setMotorTarget(long target);
long getMotorDeltaFromTarget();
