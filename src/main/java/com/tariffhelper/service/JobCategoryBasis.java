package com.tariffhelper.service;

/** Where the job category of an estimate came from. Anything but GIVEN is an inference. */
public enum JobCategoryBasis {
    GIVEN,
    STILLINGSKODE,
    JOB_TITLE
}
