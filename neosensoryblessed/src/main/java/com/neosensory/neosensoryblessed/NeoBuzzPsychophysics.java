package com.neosensory.neosensoryblessed;

// A set of tools to help produce motor encodings specifically for Neosensory Buzz
public class NeoBuzzPsychophysics {
  public static int MinIntensity = 15;
  public static int MaxIntensity = 255;
  public static final int NumMotors = 4;

  /**
   * Convert working in a linear perceived intensity space on [0 1] to a non-linear corresponding
   * motor intensity on [0 255] This is done as a linear change in motor encoding value on [0 255]
   * does not translate to a linear change in actual perceived vibrational intensity
   *
   * @param linearIntensity: float on [0 1] of a vibrational intensity to be perceived
   * @param minIntensity int on [0 255] to anchor as the lower motor intensity for perception
   * @param maxIntensity int on [0 255] to anchor as the max motor intensity for perception
   * @return int [0 255] for the motor encoding corresponding to the defined linear perceptual
   *     space.
   */
  public static int GetMotorIntensity(float linearIntensity, int minIntensity, int maxIntensity) {
    if (linearIntensity <= 0) {
      return minIntensity;
    }
    if (linearIntensity >= 1) {
      return maxIntensity;
    }
    return (int)
        (Math.expm1(linearIntensity) / (Math.E - 1) * (maxIntensity - minIntensity) + minIntensity);
  }
  /**
   * With Buzz, rather than feeling 4 discrete locations for each actuator around the wrist, it is
   * possible to use a haptic illusion to create perceived points of vibration interpolated between
   * the actuators. This method
   *
   * @param linearIntensity: float on [0 1] of a vibrational intensity to be perceived
   * @param location float on [0 1] for the location around the wrist where the perceived vibration
   *     should occur
   * @return size 4 integer array of motor intensities corresponding to the vibrational output for
   *     the illusion. This can be passed to vibrateMotors in NeosensoryBlessed.
   */
  public static int[] GetIllusionActivations(float linearIntensity, float location) {
    int[] motorFrame = new int[4];
    if (linearIntensity <= 0) {
      return motorFrame;
    }

    int motorIntensity = GetMotorIntensity(linearIntensity, MinIntensity, MaxIntensity);
    float motorLocation = location * (NumMotors - 1);
    int lowerMotorIndex = (int) (Math.floor(motorLocation));
    int upperMotorIndex = (int) (Math.ceil(motorLocation));
    float lowerDistance = Math.abs(motorLocation - lowerMotorIndex);
    float upperDistance = Math.abs(motorLocation - upperMotorIndex);
    int lowerActivation = (int) (motorIntensity * Math.sqrt(1 - lowerDistance));
    int upperActivation = (int) (motorIntensity * Math.sqrt(1 - upperDistance));

    motorFrame[lowerMotorIndex] = lowerActivation;
    motorFrame[upperMotorIndex] = upperActivation;

    return motorFrame;
  }
}
