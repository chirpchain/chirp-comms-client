package com.cherrydev.chirpcommsclient.acoustic.util;

/**
 * Created by jlunder on 6/10/15.
 */
public class AdsrEnvelope {
    private float attackSlope;
    private float attackTime;
    private float attackLevel;
    private float decaySlope;
    private float aDecayTime;
    private float sustainLevel;
    private float adSustainTime;
    private float releaseSlope;
    private float adsReleaseTime;

    public AdsrEnvelope(float attackTime,  float attackLevel, float decayTime, float sustainLevel, float sustainTime,
                        float releaseTime)
    {
        if(attackTime > 0f) {
            this.attackTime = attackTime;
            this.attackSlope = attackLevel / attackTime;
        }
        else {
            this.attackTime = 0f;
            this.attackSlope = 0f;
        }
        this.attackLevel = attackLevel;
        if(decayTime > 0f) {
            this.aDecayTime = attackTime + decayTime;
            this.decaySlope = (sustainLevel - attackLevel) / decayTime;
        }
        else {
            this.aDecayTime = this.attackTime;
            this.decaySlope = 0f;
        }
        if(sustainTime > 0f) {
            this.adSustainTime = this.aDecayTime + sustainTime;
        }
        else {
            this.adSustainTime = 0f;
        }
        this.sustainLevel = sustainLevel;
        if(releaseTime > 0f) {
            this.adsReleaseTime = this.adSustainTime + releaseTime;
            this.releaseSlope = -sustainLevel / releaseTime;
        }
        else {
            this.adsReleaseTime = 0f;
            this.releaseSlope = 0f;
        }
    }

    public float getAttackTime() { return attackTime; }
    public float getAttackLevel() { return attackLevel; }
    public float getDecayTime() { return aDecayTime; }
    public float getSustainTime() { return adSustainTime - aDecayTime; }
    public float getSustainLevel() { return sustainLevel; }
    public float getReleaseTime() { return adsReleaseTime - adSustainTime; }

    public float getEnvelopeValue(float time)
    {
        if(time < 0f) {
            return 0f;
        }
        if(time < attackTime) {
            return time * attackSlope;
        }
        else if(time < aDecayTime) {
            return attackLevel + (time - attackTime) * decaySlope;
        }
        else if(time < adSustainTime) {
            return sustainLevel;
        }
        else if(time < adsReleaseTime) {
            return sustainLevel + (time - adSustainTime) * releaseSlope;
        }
        else {
            return 0f;
        }
    }
}
