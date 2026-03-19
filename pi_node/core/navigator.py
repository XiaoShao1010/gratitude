class Navigator:
    NAV_MODE_NORMAL = "normal"
    NAV_MODE_TURN_AHEAD = "turn_ahead"
    NAV_MODE_ARRIVED = "arrived"

    NAV_EVENT_TURN_LEFT = "TURN_LEFT"
    NAV_EVENT_TURN_RIGHT = "TURN_RIGHT"
    NAV_EVENT_AHEAD = "AHEAD"
    NAV_EVENT_ARRIVED = "ARRIVED"

    def __init__(self, llm_engine, phone_comm, network_client, audio_engine):
        self.llm = llm_engine
        self.phone = phone_comm
        self.maix = network_client
        self.audio = audio_engine
        self._current_mode = self.NAV_MODE_NORMAL
        self._nav_active = False

    def extract_intent(self, user_voice_text: str) -> dict:
        intent, target = self.llm.parse_command(user_voice_text)
        return {"intent": intent, "target": target}

    def send_nav_request_to_phone(self, destination: str) -> bool:
        if not destination or destination == "null":
            return False
        sent = self.phone.send_nav_request(destination)
        if sent:
            self.audio.speak(f"好的，正在为您导航至{destination}")
            self._nav_active = True
        return sent

    def listen_phone_status(self):
        self.phone.nav_status_callback = self._on_nav_status
        self.phone.arrived_callback = self._on_arrived

    def _on_nav_status(self, status, event, distance):
        self.maix.send_mode(self._mode_for_event(event))
        if event == self.NAV_EVENT_TURN_LEFT:
            self.audio.speak(f"左转，距离{distance}米")
        elif event == self.NAV_EVENT_TURN_RIGHT:
            self.audio.speak(f"右转，距离{distance}米")
        elif event == self.NAV_EVENT_AHEAD:
            if distance < 20:
                self.audio.speak(f"直行，距离{distance}米")

    def _on_arrived(self):
        self.maix.send_mode(self.NAV_MODE_ARRIVED)
        self._nav_active = False
        self.audio.speak("已到达目的地")

    def _mode_for_event(self, event):
        if event in (self.NAV_EVENT_TURN_LEFT, self.NAV_EVENT_TURN_RIGHT):
            return self.NAV_MODE_TURN_AHEAD
        return self.NAV_MODE_NORMAL

    def adjust_maixcam_mode(self, nav_status: str):
        self._current_mode = nav_status
        self.maix.send_mode(nav_status)
