-- AFZ-Lab: Wireshark-Dissector fuer den Tuerstatus (TRDP ComId 1001)
--
-- Wiresharks eingebauter TRDP-Dissector zerlegt den PD-Header und gibt das Dataset
-- ueber die Tabelle "trdp.comid" weiter. Dieser Dissector uebernimmt ComId 1001
-- und prueft zusaetzlich die Header-FCS (CRC32), die Wireshark selbst nicht kontrolliert.
--
-- Installation: Datei kopieren nach %APPDATA%\Wireshark\plugins\ (Windows)
--               bzw. ~/.local/lib/wireshark/plugins/ (Linux), Wireshark neu starten.
-- Ohne Installation: tshark -X lua_script:tools/wireshark/afz_door_status.lua -r datei.pcap
--
-- Layout siehe common/src/main/java/ch/afzlab/common/trdp/DoorStatus.java

local COM_ID = 1001

------------------------------------------------------------------------
-- Teil 1: Dataset "Tuerstatus"
------------------------------------------------------------------------
local door = Proto("afz_door", "AFZ Tuerstatus")

local f = {
    vehicle   = ProtoField.string("afz_door.vehicle", "Fahrzeug"),
    stop      = ProtoField.uint16("afz_door.stop", "Haltestelle", base.DEC),
    speed     = ProtoField.uint16("afz_door.speed", "Geschwindigkeit (0.1 km/h)", base.DEC),
    count     = ProtoField.uint8("afz_door.count", "Anzahl Tueren", base.DEC),
    mask      = ProtoField.uint8("afz_door.mask", "Tueren offen", base.HEX),
    reserved  = ProtoField.uint16("afz_door.reserved", "Reserviert", base.HEX),
    timestamp = ProtoField.absolute_time("afz_door.timestamp", "Zeitstempel", base.UTC),
    any_open  = ProtoField.bool("afz_door.any_open", "Mindestens eine Tuer offen"),
}
local door_bits = {}
for i = 1, 8 do
    door_bits[i] = ProtoField.bool("afz_door.door" .. i, "Tuer " .. i .. " offen", 8, nil, 1 << (i - 1))
end

door.fields = { f.vehicle, f.stop, f.speed, f.count, f.mask, f.reserved, f.timestamp, f.any_open,
                table.unpack(door_bits) }

local ef_short = ProtoExpert.new("afz_door.short", "Tuerstatus zu kurz (32 Byte erwartet)",
        expert.group.MALFORMED, expert.severity.ERROR)
local ef_open_moving = ProtoExpert.new("afz_door.open_moving", "Tuer offen waehrend der Fahrt",
        expert.group.SEQUENCE, expert.severity.WARN)
door.experts = { ef_short, ef_open_moving }

function door.dissector(tvb, pinfo, tree)
    local subtree = tree:add(door, tvb(), "AFZ Tuerstatus")
    if tvb:len() < 32 then
        subtree:add_proto_expert_info(ef_short)
        return
    end

    local vehicle = tvb(0, 16):stringz()
    local stop = tvb(16, 2):uint()
    local speed = tvb(18, 2):uint()
    local count = tvb(20, 1):uint()
    local mask = tvb(21, 1):uint()
    local millis = tvb(24, 8):int64():tonumber()

    subtree:add(f.vehicle, tvb(0, 16), vehicle)
    subtree:add(f.stop, tvb(16, 2))
    subtree:add(f.speed, tvb(18, 2)):append_text(string.format(" = %.1f km/h", speed / 10))
    subtree:add(f.count, tvb(20, 1))
    local mask_item = subtree:add(f.mask, tvb(21, 1))
    for i = 1, math.min(count, 8) do
        mask_item:add(door_bits[i], tvb(21, 1))
    end
    subtree:add(f.reserved, tvb(22, 2))
    subtree:add(f.timestamp, tvb(24, 8), NSTime.new(millis // 1000, (millis % 1000) * 1000000))
    subtree:add(f.any_open, mask ~= 0):set_generated()

    if mask ~= 0 and speed > 0 then
        subtree:add_proto_expert_info(ef_open_moving)
    end

    local doors = {}
    for i = 1, count do
        doors[#doors + 1] = "T" .. i .. ((mask >> (i - 1)) & 1 == 1 and "=offen" or "=zu")
    end
    pinfo.cols.protocol = "TRDP/AFZ"
    pinfo.cols.info:append(string.format("  [%s Halt %d %.1f km/h %s]", vehicle, stop, speed / 10,
            table.concat(doors, " ")))
end

DissectorTable.get("trdp.comid"):add(COM_ID, door)

------------------------------------------------------------------------
-- Teil 2: Pruefung der Header-FCS (CRC32 ueber Byte 0..35 des TRDP-Headers)
------------------------------------------------------------------------
local fcs_check = Proto("afz_trdp_fcs", "AFZ TRDP-FCS-Pruefung")
local fcs_ok = ProtoField.bool("afz_trdp_fcs.ok", "Header-FCS korrekt")
local fcs_calc = ProtoField.uint32("afz_trdp_fcs.calculated", "Berechnete FCS", base.HEX)
fcs_check.fields = { fcs_ok, fcs_calc }
local ef_bad_fcs = ProtoExpert.new("afz_trdp_fcs.bad", "TRDP-Header-FCS falsch",
        expert.group.CHECKSUM, expert.severity.ERROR)
fcs_check.experts = { ef_bad_fcs }

local crc_table = {}
for i = 0, 255 do
    local c = i
    for _ = 1, 8 do
        c = (c & 1 == 1) and (0xEDB88320 ~ (c >> 1)) or (c >> 1)
    end
    crc_table[i] = c
end

local function crc32(range)
    local crc = 0xFFFFFFFF
    for i = 0, range:len() - 1 do
        crc = crc_table[(crc ~ range(i, 1):uint()) & 0xFF] ~ (crc >> 8)
    end
    return crc ~ 0xFFFFFFFF
end

local trdp_field = Field.new("trdp")

function fcs_check.dissector(tvb, pinfo, tree)
    for _, fi in ipairs({ trdp_field() }) do
        local range = fi.range
        if range and range:len() >= 40 then
            local expected = range(36, 4):uint()
            local actual = crc32(range(0, 36))
            local item = tree:add(fcs_check, range(36, 4), "AFZ TRDP-FCS-Pruefung")
            item:add(fcs_calc, range(36, 4), actual):set_generated()
            item:add(fcs_ok, range(36, 4), actual == expected):set_generated()
            if actual ~= expected then
                item:add_proto_expert_info(ef_bad_fcs,
                        string.format("TRDP-Header-FCS falsch: 0x%08X, berechnet 0x%08X", expected, actual))
            end
        end
    end
end

register_postdissector(fcs_check)
