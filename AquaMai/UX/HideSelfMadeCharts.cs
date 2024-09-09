﻿using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using AquaMai.Helpers;
using HarmonyLib;
using MAI2.Util;
using Manager;
using MelonLoader;
using Process;
using UnityEngine;
using Util;

namespace AquaMai.UX;

public class HideSelfMadeCharts
{
    private static Safe.ReadonlySortedDictionary<int, Manager.MaiStudio.MusicData> _musics;
    private static Safe.ReadonlySortedDictionary<int, Manager.MaiStudio.MusicData> _musicsNoneSelfMade;

    private static bool isShowSelfMadeCharts = true;
    private static bool isForceDisable;

    [HarmonyPostfix]
    [HarmonyPatch(typeof(DataManager), "GetMusics")]
    public static void GetMusics(ref Safe.ReadonlySortedDictionary<int, Manager.MaiStudio.MusicData> __result, List<string> ____targetDirs)
    {
        if (_musics is null)
        {
            // init musics for the first time
            if (__result.Count == 0) return;
            _musics = __result;
            var nonSelfMadeList = new SortedDictionary<int, Manager.MaiStudio.MusicData>();
            var officialDirs = ____targetDirs.Where(it => File.Exists(Path.Combine(it, "DataConfig.xml")) || File.Exists(Path.Combine(it, "OfficialChartsMark.txt")));
            foreach (var music in __result)
            {
                if (officialDirs.Any(it => MusicDirHelper.LookupPath(music.Value).StartsWith(it)))
                {
                    nonSelfMadeList.Add(music.Key, music.Value);
                }
            }

            _musicsNoneSelfMade = new Safe.ReadonlySortedDictionary<int, Manager.MaiStudio.MusicData>(nonSelfMadeList);
            MelonLogger.Msg($"[HideSelfMadeCharts] All music count: {__result.Count}, Official music count: {_musicsNoneSelfMade.Count}");
        }

        var stackTrace = new StackTrace(); // get call stack
        var stackFrames = stackTrace.GetFrames(); // get method calls (frames)
        if (stackFrames.All(it => it.GetMethod().DeclaringType.Name != "MusicSelectProcess")) return;
        if (isShowSelfMadeCharts && !isForceDisable) return;
        __result = _musicsNoneSelfMade;
    }

    private static int _keyPressFrames;

    [HarmonyPostfix]
    [HarmonyPatch(typeof(MusicSelectProcess), "OnUpdate")]
    public static void MusicSelectProcessOnUpdate(ref MusicSelectProcess __instance)
    {
        if (isForceDisable) return;
        if (Input.GetKey(KeyCode.Alpha7) || InputManager.GetSystemInputPush(InputManager.SystemButtonSetting.ButtonService))
        {
            _keyPressFrames++;
        }
        else if (_keyPressFrames is > 0 and < 30 && !Input.GetKey(KeyCode.Alpha7) && !InputManager.GetSystemInputPush(InputManager.SystemButtonSetting.ButtonService))
        {
            _keyPressFrames = 0;
            isShowSelfMadeCharts = !isShowSelfMadeCharts;
            MelonLogger.Msg($"[HideSelfMadeCharts] isShowSelfMadeCharts: {isShowSelfMadeCharts}");
            SharedInstances.ProcessDataContainer.processManager.AddProcess(new FadeProcess(SharedInstances.ProcessDataContainer, __instance, new MusicSelectProcess(SharedInstances.ProcessDataContainer)));
            Task.Run(async () =>
            {
                await Task.Delay(1000);
                MessageHelper.ShowMessage($"{(isShowSelfMadeCharts ? "Show" : "Hide")} Self-Made Charts");
            });
        }
        else
        {
            _keyPressFrames = 0;
        }
    }

    [HarmonyPrefix]
    [HarmonyPatch(typeof(MusicSelectProcess), "OnStart")]
    public static void MusicSelectProcessOnStart(ref MusicSelectProcess __instance)
    {
        if (File.Exists(Path.Combine(Environment.CurrentDirectory, "LocalAssets", "SelfMadeChartsDenyUsers.txt")))
        {
            var userIds = File.ReadAllLines(Path.Combine(Environment.CurrentDirectory, "LocalAssets", "SelfMadeChartsDenyUsers.txt"));
            for (var i = 0; i < 2; i++)
            {
                var user = Singleton<UserDataManager>.Instance.GetUserData(i);
                if (!user.IsEntry) continue;
                if (!userIds.Contains(user.Detail.UserID.ToString())) continue;
                isForceDisable = true;
                return;
            }
        }

        if (File.Exists(Path.Combine(Environment.CurrentDirectory, "LocalAssets", "SelfMadeChartsWhiteListUsers.txt")))
        {
            var userIds = File.ReadAllLines(Path.Combine(Environment.CurrentDirectory, "LocalAssets", "SelfMadeChartsWhiteListUsers.txt"));
            for (var i = 0; i < 2; i++)
            {
                var user = Singleton<UserDataManager>.Instance.GetUserData(i);
                if (!user.IsEntry) continue;
                if (userIds.Contains(user.Detail.UserID.ToString())) continue;
                isForceDisable = true;
                return;
            }
        }

        isForceDisable = false;
    }


    [HarmonyPostfix]
    [HarmonyPatch(typeof(EntryProcess), "OnStart")]
    public static void EntryProcessOnStart(ref EntryProcess __instance)
    {
        // reset status on login
        isShowSelfMadeCharts = true;
    }
}
