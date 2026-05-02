import React, { useEffect, useRef } from 'react';
import {
  View,
  Text,
  ActivityIndicator,
  StyleSheet,
  Animated,
} from 'react-native';

interface LoadingScreenProps {
  /** Called after the 4-second display window completes. */
  onFinished: () => void;
}

/**
 * LoadingScreen — "For Grace"
 *
 * Shows an elegant splash with a slow fade-in of the dedication and verse,
 * then calls onFinished after 4 seconds.
 */
const LoadingScreen: React.FC<LoadingScreenProps> = ({ onFinished }) => {
  const fadeAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    // Fade both text elements in over 2 seconds
    Animated.timing(fadeAnim, {
      toValue: 1,
      duration: 2000,
      useNativeDriver: true,
    }).start();

    // Hold screen for 4 seconds, then signal completion
    const timer = setTimeout(() => {
      onFinished();
    }, 4000);

    return () => clearTimeout(timer);
  }, [fadeAnim, onFinished]);

  return (
    <View style={styles.container}>
      <View style={styles.content}>
        <Animated.Text style={[styles.title, { opacity: fadeAnim }]}>
          For Grace
        </Animated.Text>

        <Animated.Text style={[styles.verse, { opacity: fadeAnim }]}>
          {'"May he give you the desire of your heart\nand make all your plans succeed."\n\nPsalm 20:4'}
        </Animated.Text>
      </View>

      <ActivityIndicator
        size="small"
        color="rgba(255, 255, 255, 0.6)"
        style={styles.indicator}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#121212',
    alignItems: 'center',
    justifyContent: 'center',
  },
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
  },
  title: {
    fontSize: 42,
    fontWeight: '300',
    color: '#FFFFFF',
    fontStyle: 'italic',
    letterSpacing: 3,
    marginBottom: 32,
    textAlign: 'center',
  },
  verse: {
    fontSize: 14,
    color: 'rgba(255, 255, 255, 0.65)',
    letterSpacing: 1.5,
    lineHeight: 24,
    textAlign: 'center',
    fontWeight: '300',
  },
  indicator: {
    marginBottom: 48,
  },
});

export default LoadingScreen;

