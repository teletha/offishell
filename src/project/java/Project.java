/*
 * Copyright (C) 2022 The OFFISHELL Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
public class Project extends bee.api.Project {

    {
        product("com.github.teletha", "officelove", "1.1");

        require("com.github.teletha", "sinobu");
        require("com.github.teletha", "psychopath");
        require("org.apache.poi", "poi-ooxml");
        require("com.github.teletha", "antibug").atTest();

        versionControlSystem("https://github.com/teletha/officelove");
    }
}