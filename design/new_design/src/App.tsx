import React, { useState, useEffect } from 'react';
import { 
  Play, Settings, Video, List, X, Loader2, Camera, 
  Mic, Monitor, MoreVertical, ChevronRight, ChevronLeft,
  CircleDot, Trash2, CheckCircle2, Circle, Pause,
  RotateCcw, RotateCw, ArchiveRestore, Gauge, ArrowLeft
} from 'lucide-react';

type Screen = 'loading' | 'home' | 'list' | 'settings' | 'trash' | 'player';

interface Recording {
  id: number;
  title: string;
  date: string;
  duration: string;
  size: string;
  thumb: string;
  deleted: boolean;
}

const initialRecordings: Recording[] = [
  { id: 1, title: 'Project Demo Presentation', date: 'Today, 10:24 AM', duration: '12:45', size: '450 MB', thumb: 'https://images.unsplash.com/photo-1542744094-24638ea0b3b5?w=800&h=450&fit=crop', deleted: false },
  { id: 2, title: 'UI Design Review', date: 'Yesterday, 4:30 PM', duration: '08:12', size: '210 MB', thumb: 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=800&h=450&fit=crop', deleted: false },
  { id: 3, title: 'Gameplay Walkthrough', date: 'Mon, 8:15 PM', duration: '45:20', size: '1.2 GB', thumb: 'https://images.unsplash.com/photo-1552820728-8b83bb6b7738?w=800&h=450&fit=crop', deleted: false },
  { id: 4, title: 'Bug Reproduction #401', date: 'Sun, 2:10 PM', duration: '02:05', size: '85 MB', thumb: 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=800&h=450&fit=crop', deleted: false },
];

export default function App() {
  const [currentScreen, setCurrentScreen] = useState<Screen>('loading');
  const [isDialOpen, setIsDialOpen] = useState(false);
  const [recordings, setRecordings] = useState<Recording[]>(initialRecordings);
  const [activeVideoId, setActiveVideoId] = useState<number | null>(null);

  useEffect(() => {
    if (currentScreen === 'loading') {
      const timer = setTimeout(() => setCurrentScreen('home'), 2000);
      return () => clearTimeout(timer);
    }
  }, [currentScreen]);

  const activeRecordings = recordings.filter(r => !r.deleted);
  const trashedRecordings = recordings.filter(r => r.deleted);
  const activeVideo = recordings.find(r => r.id === activeVideoId);

  const handleMoveToTrash = (ids: number[]) => {
    setRecordings(recordings.map(r => ids.includes(r.id) ? { ...r, deleted: true } : r));
  };

  const handleRestore = (ids: number[]) => {
    setRecordings(recordings.map(r => ids.includes(r.id) ? { ...r, deleted: false } : r));
  };

  const handlePermanentDelete = (ids: number[]) => {
    setRecordings(recordings.filter(r => !ids.includes(r.id)));
  };

  const handlePlay = (id: number) => {
    setActiveVideoId(id);
    setCurrentScreen('player');
  };

  if (currentScreen === 'player' && activeVideo) {
    return <PlayerScreen video={activeVideo} onBack={() => setCurrentScreen('list')} />;
  }

  return (
    <div className="w-full h-screen bg-background text-foreground overflow-hidden flex flex-col font-sans select-none">
      {currentScreen === 'loading' && <LoadingScreen />}
      
      {currentScreen !== 'loading' && (
        <div className="flex w-full h-full">
          {/* Sidebar Navigation for Tablet Landscape */}
          <nav className="hidden landscape:flex flex-col w-24 bg-card border-r border-border h-full py-6 items-center gap-8 z-10">
            <div className="w-12 h-12 bg-primary/10 rounded-full flex items-center justify-center text-primary">
              <CircleDot size={24} />
            </div>
            <div className="flex flex-col gap-6 w-full items-center mt-4">
              <NavButton active={currentScreen === 'home'} icon={<Video size={24} />} onClick={() => setCurrentScreen('home')} />
              <NavButton active={currentScreen === 'list'} icon={<List size={24} />} onClick={() => setCurrentScreen('list')} />
            </div>
            <div className="mt-auto mb-4">
              <NavButton active={currentScreen === 'settings'} icon={<Settings size={24} />} onClick={() => setCurrentScreen('settings')} />
            </div>
          </nav>

          {/* Main Content Area */}
          <main className="flex-1 h-full overflow-y-auto relative bg-background">
            {/* Top Bar for Portrait */}
            <div className="landscape:hidden flex items-center justify-between p-4 border-b border-border bg-card sticky top-0 z-20">
              <div className="flex items-center gap-3 text-primary font-semibold">
                <CircleDot size={20} />
                <span>RecPro</span>
              </div>
              <button onClick={() => setCurrentScreen('settings')} className="p-2 bg-secondary rounded-full">
                <Settings size={20} />
              </button>
            </div>

            {currentScreen === 'home' && <HomeScreen />}
            {currentScreen === 'list' && (
              <ListScreen 
                recordings={activeRecordings} 
                onPlay={handlePlay} 
                onMoveToTrash={handleMoveToTrash}
                onGoToTrash={() => setCurrentScreen('trash')}
              />
            )}
            {currentScreen === 'trash' && (
              <TrashScreen 
                recordings={trashedRecordings}
                onRestore={handleRestore}
                onPermanentDelete={handlePermanentDelete}
                onBack={() => setCurrentScreen('list')}
              />
            )}
            {currentScreen === 'settings' && <SettingsScreen onBack={() => setCurrentScreen('home')} />}
            
            {/* Speed Dial FAB */}
            {(currentScreen === 'home' || currentScreen === 'list') && (
              <SpeedDial isOpen={isDialOpen} toggle={() => setIsDialOpen(!isDialOpen)} />
            )}
          </main>

          {/* Bottom Nav for Portrait */}
          <nav className="landscape:hidden flex w-full h-20 bg-card border-t border-border items-center justify-around absolute bottom-0 z-30">
            <NavButton active={currentScreen === 'home'} icon={<Video size={24} />} onClick={() => setCurrentScreen('home')} label="Record" />
            <NavButton active={currentScreen === 'list' || currentScreen === 'trash'} icon={<List size={24} />} onClick={() => setCurrentScreen('list')} label="Library" />
          </nav>
        </div>
      )}
    </div>
  );
}

function NavButton({ active, icon, onClick, label }: { active: boolean, icon: React.ReactNode, onClick: () => void, label?: string }) {
  return (
    <button 
      onClick={onClick}
      className={`flex flex-col items-center justify-center p-3 rounded-xl transition-all duration-200 ${
        active ? 'bg-primary text-primary-foreground shadow-lg shadow-primary/25' : 'text-muted-foreground hover:bg-secondary hover:text-foreground'
      }`}
    >
      {icon}
      {label && <span className="text-[10px] mt-1 font-medium">{label}</span>}
    </button>
  );
}

function LoadingScreen() {
  return (
    <div className="w-full h-full flex flex-col items-center justify-center bg-background">
      <div className="relative flex items-center justify-center w-24 h-24 mb-6">
        <div className="absolute inset-0 border-4 border-secondary rounded-full"></div>
        <div className="absolute inset-0 border-4 border-primary rounded-full border-t-transparent animate-spin"></div>
        <CircleDot size={32} className="text-primary" />
      </div>
      <h1 className="text-2xl font-semibold tracking-tight text-foreground">RecPro</h1>
      <p className="text-muted-foreground mt-2 text-sm">Initializing capture engine...</p>
    </div>
  );
}

function HomeScreen() {
  const [selectedMode, setSelectedMode] = useState<'fullscreen' | 'app'>('fullscreen');

  return (
    <div className="p-6 md:p-12 w-full max-w-5xl mx-auto flex flex-col h-full md:h-auto portrait:pb-28 landscape:pb-12">
      <header className="mb-10">
        <h2 className="text-3xl md:text-4xl font-bold tracking-tight mb-2">Ready to Record</h2>
        <p className="text-muted-foreground">Select a recording mode or adjust settings before you start.</p>
      </header>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div 
          onClick={() => setSelectedMode('fullscreen')}
          className={`bg-card border rounded-2xl p-6 flex flex-col items-start gap-4 transition-all cursor-pointer group ${
            selectedMode === 'fullscreen' ? 'border-primary ring-1 ring-primary shadow-lg shadow-primary/10' : 'border-border hover:border-primary/50'
          }`}
        >
          <div className={`w-14 h-14 rounded-xl flex items-center justify-center transition-colors ${
            selectedMode === 'fullscreen' ? 'bg-primary text-primary-foreground' : 'bg-secondary text-foreground group-hover:bg-primary/20 group-hover:text-primary'
          }`}>
            <Monitor size={28} />
          </div>
          <div>
            <h3 className="text-xl font-semibold mb-1">Full Screen</h3>
            <p className="text-sm text-muted-foreground">Capture the entire tablet display. Ideal for tutorials and gameplay.</p>
          </div>
        </div>

        <div 
          onClick={() => setSelectedMode('app')}
          className={`bg-card border rounded-2xl p-6 flex flex-col items-start gap-4 transition-all cursor-pointer group ${
            selectedMode === 'app' ? 'border-primary ring-1 ring-primary shadow-lg shadow-primary/10' : 'border-border hover:border-primary/50'
          }`}
        >
          <div className={`w-14 h-14 rounded-xl flex items-center justify-center transition-colors ${
            selectedMode === 'app' ? 'bg-primary text-primary-foreground' : 'bg-secondary text-foreground group-hover:bg-primary/20 group-hover:text-primary'
          }`}>
            <List size={28} />
          </div>
          <div>
            <h3 className="text-xl font-semibold mb-1">Specific App</h3>
            <p className="text-sm text-muted-foreground">Record only a selected application window, keeping notifications private.</p>
          </div>
        </div>

        <div className="bg-card border border-border rounded-2xl p-6 col-span-1 md:col-span-2 mt-4 flex flex-col">
          <h3 className="text-lg font-medium mb-4 flex items-center gap-2">
            <Settings size={18} className="text-muted-foreground" />
            Active Configuration
          </h3>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <ConfigBadge icon={<Monitor size={16} />} label="Resolution" value="1440p" />
            <ConfigBadge icon={<Play size={16} />} label="Framerate" value="60 FPS" />
            <ConfigBadge icon={<Mic size={16} />} label="Microphone" value="On" />
            <ConfigBadge icon={<Camera size={16} />} label="Face Cam" value="Off" />
          </div>
        </div>
      </div>
    </div>
  );
}

function ConfigBadge({ icon, label, value }: { icon: React.ReactNode, label: string, value: string }) {
  return (
    <div className="flex items-center gap-3 bg-background rounded-lg p-3 border border-border">
      <div className="text-primary">{icon}</div>
      <div className="flex flex-col">
        <span className="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">{label}</span>
        <span className="text-sm font-medium">{value}</span>
      </div>
    </div>
  );
}

function ListScreen({ recordings, onPlay, onMoveToTrash, onGoToTrash }: { recordings: Recording[], onPlay: (id: number) => void, onMoveToTrash: (ids: number[]) => void, onGoToTrash: () => void }) {
  const [isSelectionMode, setIsSelectionMode] = useState(false);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);

  const toggleSelection = (id: number) => {
    if (selectedIds.includes(id)) {
      setSelectedIds(selectedIds.filter(selectedId => selectedId !== id));
    } else {
      setSelectedIds([...selectedIds, id]);
    }
  };

  const handleAction = (item: Recording) => {
    if (isSelectionMode) toggleSelection(item.id);
    else onPlay(item.id);
  };

  const handleDeleteSelected = () => {
    onMoveToTrash(selectedIds);
    setIsSelectionMode(false);
    setSelectedIds([]);
  };

  return (
    <div className="p-6 md:p-12 w-full max-w-6xl mx-auto h-full md:h-auto portrait:pb-28 landscape:pb-12">
      <header className="mb-8 flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <h2 className="text-3xl md:text-4xl font-bold tracking-tight mb-2">Library</h2>
          <p className="text-muted-foreground">Manage and share your captured recordings.</p>
        </div>
        <div className="flex items-center gap-3">
          {isSelectionMode ? (
            <>
              <span className="text-sm text-muted-foreground mr-2">{selectedIds.length} selected</span>
              <button onClick={() => setIsSelectionMode(false)} className="px-4 py-2 bg-secondary text-foreground rounded-lg font-medium text-sm">Cancel</button>
              <button 
                onClick={handleDeleteSelected} 
                disabled={selectedIds.length === 0}
                className="px-4 py-2 bg-primary text-primary-foreground rounded-lg font-medium text-sm flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <Trash2 size={16} /> Delete
              </button>
            </>
          ) : (
            <>
              <button onClick={() => setIsSelectionMode(true)} className="px-4 py-2 bg-secondary text-foreground rounded-lg font-medium text-sm">Select</button>
              <button onClick={onGoToTrash} className="p-2 bg-secondary text-foreground rounded-lg">
                <Trash2 size={20} />
              </button>
            </>
          )}
        </div>
      </header>

      {recordings.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
          <Video size={48} className="mb-4 opacity-20" />
          <p>No recordings found.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {recordings.map((rec) => (
            <div 
              key={rec.id} 
              onClick={() => handleAction(rec)}
              className={`bg-card border rounded-2xl overflow-hidden group transition-colors cursor-pointer relative ${
                selectedIds.includes(rec.id) ? 'border-primary' : 'border-border hover:border-primary/50'
              }`}
            >
              {isSelectionMode && (
                <div className="absolute top-3 left-3 z-10">
                  {selectedIds.includes(rec.id) ? (
                    <CheckCircle2 size={24} className="text-primary fill-primary/20" />
                  ) : (
                    <Circle size={24} className="text-white drop-shadow-md" />
                  )}
                </div>
              )}
              <div className="relative w-full h-40 bg-secondary">
                <img src={rec.thumb} alt={rec.title} className={`w-full h-full object-cover transition-opacity ${selectedIds.includes(rec.id) ? 'opacity-50' : 'opacity-80 group-hover:opacity-100'}`} />
                <div className="absolute bottom-2 right-2 bg-black/70 backdrop-blur-sm text-white text-xs px-2 py-1 rounded font-mono">
                  {rec.duration}
                </div>
                {!isSelectionMode && (
                  <div className="absolute inset-0 bg-primary/20 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
                    <div className="w-12 h-12 bg-primary rounded-full flex items-center justify-center text-white shadow-lg">
                      <Play size={20} className="ml-1" />
                    </div>
                  </div>
                )}
              </div>
              <div className="p-4">
                <h3 className="font-semibold truncate pr-2">{rec.title}</h3>
                <div className="flex items-center justify-between text-xs text-muted-foreground mt-2">
                  <span>{rec.date}</span>
                  <span>{rec.size}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function TrashScreen({ recordings, onRestore, onPermanentDelete, onBack }: { recordings: Recording[], onRestore: (ids: number[]) => void, onPermanentDelete: (ids: number[]) => void, onBack: () => void }) {
  const [selectedIds, setSelectedIds] = useState<number[]>([]);

  const toggleSelection = (id: number) => {
    if (selectedIds.includes(id)) {
      setSelectedIds(selectedIds.filter(selectedId => selectedId !== id));
    } else {
      setSelectedIds([...selectedIds, id]);
    }
  };

  const handleRestoreSelected = () => {
    onRestore(selectedIds);
    setSelectedIds([]);
  };

  const handleDeleteSelected = () => {
    onPermanentDelete(selectedIds);
    setSelectedIds([]);
  };

  return (
    <div className="p-6 md:p-12 w-full max-w-6xl mx-auto h-full portrait:pb-28 landscape:pb-12 flex flex-col">
      <header className="mb-8 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <button onClick={onBack} className="p-2 bg-secondary rounded-full text-foreground hover:bg-secondary/80">
            <ArrowLeft size={20} />
          </button>
          <div>
            <h2 className="text-3xl font-bold tracking-tight">Trash</h2>
            <p className="text-muted-foreground text-sm mt-1">Items here will be permanently deleted after 30 days.</p>
          </div>
        </div>
        
        <div className="flex items-center gap-3">
          {selectedIds.length > 0 && (
            <>
              <span className="text-sm text-muted-foreground mr-2">{selectedIds.length} selected</span>
              <button onClick={handleRestoreSelected} className="px-4 py-2 bg-secondary text-foreground rounded-lg font-medium text-sm flex items-center gap-2">
                <ArchiveRestore size={16} /> Restore
              </button>
              <button onClick={handleDeleteSelected} className="px-4 py-2 bg-red-900/40 text-red-500 rounded-lg font-medium text-sm flex items-center gap-2">
                <Trash2 size={16} /> Delete Forever
              </button>
            </>
          )}
        </div>
      </header>

      {recordings.length === 0 ? (
        <div className="flex-1 flex flex-col items-center justify-center text-muted-foreground">
          <Trash2 size={48} className="mb-4 opacity-20" />
          <p>Trash is empty.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {recordings.map((rec) => (
            <div 
              key={rec.id} 
              onClick={() => toggleSelection(rec.id)}
              className={`bg-card border rounded-2xl overflow-hidden cursor-pointer relative transition-all ${
                selectedIds.includes(rec.id) ? 'border-primary opacity-100' : 'border-border opacity-70 hover:opacity-100'
              }`}
            >
              <div className="absolute top-3 left-3 z-10">
                {selectedIds.includes(rec.id) ? (
                  <CheckCircle2 size={24} className="text-primary fill-primary/20" />
                ) : (
                  <Circle size={24} className="text-white drop-shadow-md" />
                )}
              </div>
              <div className="relative w-full h-40 bg-secondary grayscale">
                <img src={rec.thumb} alt={rec.title} className="w-full h-full object-cover opacity-50" />
              </div>
              <div className="p-4 bg-card">
                <h3 className="font-semibold truncate line-through text-muted-foreground">{rec.title}</h3>
                <div className="flex items-center justify-between text-xs text-muted-foreground/70 mt-2">
                  <span>{rec.size}</span>
                  <span className="text-red-400">Deleted</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function PlayerScreen({ video, onBack }: { video: Recording, onBack: () => void }) {
  const [isPlaying, setIsPlaying] = useState(true);
  const [speed, setSpeed] = useState(1);
  const [showControls, setShowControls] = useState(true);

  // Speed options
  const speeds = [0.5, 1, 1.25, 1.5, 2];
  const cycleSpeed = () => {
    const nextIndex = (speeds.indexOf(speed) + 1) % speeds.length;
    setSpeed(speeds[nextIndex]);
  };

  // Auto-hide controls when playing
  useEffect(() => {
    let timeout: NodeJS.Timeout;
    if (isPlaying && showControls) {
      timeout = setTimeout(() => setShowControls(false), 3000);
    }
    return () => clearTimeout(timeout);
  }, [isPlaying, showControls]);

  return (
    <div 
      className="absolute inset-0 bg-black z-50 flex flex-col items-center justify-center overflow-hidden font-sans"
      onClick={() => setShowControls(true)}
    >
      {/* Top Bar */}
      <div className={`absolute top-0 inset-x-0 p-6 flex items-center justify-between bg-gradient-to-b from-black/80 to-transparent transition-opacity duration-300 z-10 ${showControls ? 'opacity-100' : 'opacity-0'}`}>
        <div className="flex items-center gap-4">
          <button onClick={onBack} className="p-3 bg-white/10 hover:bg-white/20 backdrop-blur-md rounded-full text-white transition-colors">
            <ArrowLeft size={24} />
          </button>
          <h2 className="text-white font-medium text-lg drop-shadow-md">{video.title}</h2>
        </div>
        <button className="p-3 text-white hover:bg-white/10 rounded-full transition-colors">
          <MoreVertical size={24} />
        </button>
      </div>

      {/* Video Mock (Image) */}
      <div className="w-full h-full relative flex items-center justify-center">
        <img src={video.thumb} alt="Video content" className="w-full h-full object-contain" />
      </div>

      {/* Main Playback Controls Center Overlay */}
      <div className={`absolute inset-0 flex items-center justify-center gap-12 transition-opacity duration-300 pointer-events-none ${showControls ? 'opacity-100' : 'opacity-0'}`}>
        <button className="p-4 bg-black/40 hover:bg-black/60 backdrop-blur-md rounded-full text-white pointer-events-auto transition-transform active:scale-95">
          <RotateCcw size={36} />
        </button>
        <button 
          onClick={(e) => { e.stopPropagation(); setIsPlaying(!isPlaying); }}
          className="w-24 h-24 bg-primary/90 hover:bg-primary backdrop-blur-md rounded-full text-white flex items-center justify-center pointer-events-auto shadow-2xl transition-transform active:scale-95"
        >
          {isPlaying ? <Pause size={48} className="fill-current" /> : <Play size={48} className="fill-current ml-2" />}
        </button>
        <button className="p-4 bg-black/40 hover:bg-black/60 backdrop-blur-md rounded-full text-white pointer-events-auto transition-transform active:scale-95">
          <RotateCw size={36} />
        </button>
      </div>

      {/* Bottom Control Bar */}
      <div className={`absolute bottom-0 inset-x-0 p-6 pt-24 bg-gradient-to-t from-black/90 via-black/50 to-transparent transition-opacity duration-300 ${showControls ? 'opacity-100' : 'opacity-0'}`}>
        {/* Progress Bar Mock */}
        <div className="flex items-center gap-4 mb-6">
          <span className="text-white text-xs font-mono">03:14</span>
          <div className="flex-1 h-1.5 bg-white/30 rounded-full overflow-hidden relative cursor-pointer">
            <div className="absolute top-0 left-0 h-full bg-primary w-1/4 rounded-full"></div>
          </div>
          <span className="text-white/60 text-xs font-mono">{video.duration}</span>
        </div>

        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <button className="p-2 text-white/80 hover:text-white transition-colors">
              <Settings size={24} />
            </button>
          </div>
          <div className="flex items-center gap-4">
            {/* Speed Toggle */}
            <button onClick={(e) => { e.stopPropagation(); cycleSpeed(); }} className="flex items-center gap-2 px-3 py-1.5 bg-white/10 hover:bg-white/20 rounded-lg text-white text-sm font-medium transition-colors">
              <Gauge size={16} />
              {speed}x
            </button>
            <button className="p-2 text-white/80 hover:text-white transition-colors">
              <Monitor size={24} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ... SettingsScreen, SpeedDial, and other components remain below ...
function SettingsScreen({ onBack }: { onBack: () => void }) {
  const [sysAudio, setSysAudio] = useState(true);
  const [faceCam, setFaceCam] = useState(false);

  return (
    <div className="p-6 md:p-12 w-full max-w-4xl mx-auto h-full portrait:pb-28 landscape:pb-12">
      <header className="mb-10 flex items-center gap-4">
        <button onClick={onBack} className="landscape:hidden p-2 bg-secondary rounded-full text-foreground">
          <ChevronLeft size={20} />
        </button>
        <div>
          <h2 className="text-3xl md:text-4xl font-bold tracking-tight mb-2">Settings</h2>
          <p className="text-muted-foreground">Configure video, audio, and application preferences.</p>
        </div>
      </header>

      <div className="flex flex-col gap-6">
        <SettingSection title="Video Quality">
          <SettingItem label="Resolution" value="1440p (QHD)" hasArrow />
          <SettingItem label="Frame Rate" value="60 FPS" hasArrow />
          <SettingItem label="Face Cam" isToggle isChecked={faceCam} onToggle={() => setFaceCam(!faceCam)} />
        </SettingSection>
        
        <SettingSection title="Audio Sources">
          <SettingItem label="Microphone Input" value="External Mic (USB)" hasArrow />
          <SettingItem label="System Audio" isToggle isChecked={sysAudio} onToggle={() => setSysAudio(!sysAudio)} />
        </SettingSection>
      </div>
    </div>
  );
}

function SettingSection({ title, children }: { title: string, children: React.ReactNode }) {
  return (
    <div className="bg-card border border-border rounded-2xl overflow-hidden">
      <div className="px-6 py-4 bg-secondary/50 border-b border-border">
        <h3 className="font-semibold text-foreground">{title}</h3>
      </div>
      <div className="flex flex-col divide-y divide-border">
        {children}
      </div>
    </div>
  );
}

function SettingItem({ 
  label, value, hasArrow, isToggle, isChecked, onToggle 
}: { 
  label: string, value?: string, hasArrow?: boolean, isToggle?: boolean, isChecked?: boolean, onToggle?: () => void 
}) {
  return (
    <div 
      onClick={isToggle ? onToggle : undefined}
      className="px-6 py-4 flex items-center justify-between hover:bg-secondary/30 cursor-pointer transition-colors active:bg-secondary/50"
    >
      <span className="text-sm font-medium">{label}</span>
      <div className="flex items-center gap-3">
        {value && <span className="text-sm text-muted-foreground">{value}</span>}
        
        {isToggle && (
          <div className={`w-10 h-6 rounded-full flex items-center transition-colors px-1 ${isChecked ? 'bg-primary' : 'bg-muted'}`}>
            <div className={`w-4 h-4 bg-white rounded-full shadow-sm transition-transform ${isChecked ? 'translate-x-4' : 'translate-x-0'}`} />
          </div>
        )}

        {hasArrow && <ChevronRight size={16} className="text-muted-foreground/50" />}
      </div>
    </div>
  );
}

function SpeedDial({ isOpen, toggle }: { isOpen: boolean, toggle: () => void }) {
  return (
    <div className="fixed portrait:bottom-28 landscape:bottom-8 portrait:right-6 landscape:right-10 flex flex-col-reverse items-center gap-4 z-50">
      <button 
        onClick={toggle}
        className={`w-16 h-16 rounded-full flex items-center justify-center shadow-2xl transition-all duration-300 ${
          isOpen ? 'bg-secondary text-foreground rotate-45' : 'bg-primary text-primary-foreground hover:scale-105'
        }`}
      >
        {isOpen ? <X size={28} /> : <CircleDot size={28} />}
      </button>

      <div className={`flex flex-col gap-3 transition-all duration-300 origin-bottom ${
        isOpen ? 'opacity-100 scale-100 translate-y-0' : 'opacity-0 scale-50 translate-y-10 pointer-events-none'
      }`}>
        <DialAction icon={<Camera size={20} />} label="Screenshot" />
        <DialAction icon={<Mic size={20} />} label="Voice Only" />
        <DialAction icon={<Video size={20} />} label="Start Screen Record" primary />
      </div>
    </div>
  );
}

function DialAction({ icon, label, primary }: { icon: React.ReactNode, label: string, primary?: boolean }) {
  return (
    <div className="flex items-center gap-3 flex-row-reverse group cursor-pointer">
      <button className={`w-12 h-12 rounded-full flex items-center justify-center shadow-lg transition-transform group-hover:scale-110 ${
        primary ? 'bg-primary text-white' : 'bg-card text-foreground border border-border'
      }`}>
        {icon}
      </button>
      <span className="bg-card border border-border px-3 py-1.5 rounded-lg text-sm font-medium shadow-sm opacity-0 group-hover:opacity-100 transition-opacity -translate-x-2 group-hover:translate-x-0">
        {label}
      </span>
    </div>
  );
}